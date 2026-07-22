package com.solo.gb28181.service.impl;

import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.media.properties.MediaProperties;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.media.entity.MediaServer;
import com.solo.media.MediaUrlBuilder;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.ssrc.SsrcFactory;
import com.solo.gb28181.request.PlayStartRequest;
import com.solo.gb28181.request.PlayStopRequest;
import com.solo.gb28181.response.PlayResultResponse;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.service.GbPlayService;
import com.solo.gb28181.sip.SipLayer;
import com.solo.gb28181.transmit.cmd.SdpBuilder;
import com.solo.gb28181.transmit.cmd.SipCommander;
import com.solo.gb28181.transmit.cmd.SipRequestHeaderProvider;
import com.solo.gb28181.utils.SipUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sip.header.CallIdHeader;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 实时点播编排实现
 *
 * <h3>关键流程（start）</h3>
 * <ol>
 *     <li>校验设备 / 通道 / 节点；幂等性：已 READY 直接返回</li>
 *     <li>{@link SsrcFactory#acquire} 申请 SSRC</li>
 *     <li>{@link ZlmRestClient#openRtpServer} 申请收流端口</li>
 *     <li>入库 {@link GbInviteSession}(INVITING) — Call-Id / from-tag 此时已确定</li>
 *     <li>注册 {@link CompletableFuture} 到 {@link #pending}（key=streamId）</li>
 *     <li>{@link SipCommander#sendInvite} 发 INVITE；ACK 由 InviteResponseProcessor 异步处理</li>
 *     <li>future 同步等 {@code inviteTimeoutSeconds} 秒：on_stream_changed 回调或超时</li>
 * </ol>
 *
 * <h3>失败 / 超时清扫顺序（严格）</h3>
 * <p>1. closeRtpServer（占着 RTP 端口的是 ZLM，先停止它）
 * 2. release SSRC（先 close 后 release，避免新会话拿到同一 ssrc 时 openRtpServer 拒绝）
 * 3. 删 session
 * 4. 摘除 pending future</p>
 *
 * <p>大量公共清扫 / 节点选择 / 字段反查代码抽到 {@link GbInviteSupport}，与回放共用。</p>
 * <p>autoApplyPlay 异步起点播功能，由专属业务线程池执行不阻塞 ZLM hook 回调。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbPlayServiceImpl implements GbPlayService {

    private static final String APP = "rtp";
    private static final String LOG_TAG = "实时点播";

    private final Gb28181Properties sipProperties;
    private final MediaProperties mediaProperties;
    private final SipLayer sipLayer;
    private final GbInviteSessionService inviteSessionService;
    private final SsrcFactory ssrcFactory;
    private final ZlmRestClient zlmRestClient;
    private final SipCommander sipCommander;
    private final SipRequestHeaderProvider headerProvider;
    private final MediaUrlBuilder urlBuilder;
    private final GbInviteSupport support;
    private final GbDeviceChannelService channelService;
    private final java.util.concurrent.ThreadPoolExecutor gb28181BizExecutor;

    /** 等待 on_stream_changed 的 future，key=streamId */
    private final Map<String, CompletableFuture<PlayResultResponse>> pending = new ConcurrentHashMap<>();

    // ============================================================
    //  启动清扫：把残留的 INVITING/READY 全部置 FAILED，避免重启后僵尸会话
    // ============================================================

    @PostConstruct
    void cleanupOnStart() {
        List<GbInviteSession> active = inviteSessionService.listAllActive();
        if (active.isEmpty()) {
            return;
        }
        // 进程重启：所有运行中会话都失效，直接物理清掉。
        // ZLM 那边 RTP 端口、SSRC 是否也残留得在 PlayService 重新接管时按需 closeRtpServer，
        // 简化起见这里只清状态；ZLM 端的残留靠下次同 streamId 的 openRtpServer 重试逻辑兜底。
        for (GbInviteSession s : active) {
            inviteSessionService.removeByStreamId(s.getStreamId());
        }
        log.info("[实时点播] 启动清扫：{} 条残留会话已删除", active.size());
    }

    // ============================================================
    //  start / stop
    // ============================================================

    @Override
    public PlayResultResponse start(PlayStartRequest req) {
        if (req == null || req.getDeviceId() == null || req.getChannelId() == null) {
            throw new ServiceException("deviceId / channelId 不能为空");
        }

        GbDevice device = support.device(req.getDeviceId());
        if (device == null) {
            throw new ServiceException("设备不存在: " + req.getDeviceId());
        }
        if (device.getStatus() == null || device.getStatus() != 1) {
            throw new ServiceException("设备离线，无法点播");
        }
        GbDeviceChannel channel = support.channel(req.getDeviceId(), req.getChannelId());
        if (channel == null) {
            throw new ServiceException("通道不存在: " + req.getChannelId());
        }
        if (!"ON".equalsIgnoreCase(channel.getStatus())) {
            throw new ServiceException("通道离线，无法点播");
        }

        // 幂等：同一个通道同一时间只允许一个活跃点播
        List<GbInviteSession> exist = inviteSessionService.listActiveByDeviceAndChannel(
                req.getDeviceId(), req.getChannelId());
        for (GbInviteSession s : exist) {
            if (GbInviteSession.STATUS_READY.equals(s.getStatus())) {
                // 已经就绪，直接复用，避免重复邀请浪费资源
                log.info("[实时点播] 复用已就绪会话 stream={}", s.getStreamId());
                return toResult(s);
            }
            if (GbInviteSession.STATUS_INVITING.equals(s.getStatus())) {
                // 正在邀请中，告诉前端稍候，不要重复发起 INVITE 导致重复流
                log.warn("[实时点播] 该通道正在点播邀请中，请稍后重试 device={} channel={}",
                        req.getDeviceId(), req.getChannelId());
                throw new ServiceException("该通道正在请求点播，请稍后重试");
            }
        }

        MediaServer server = support.pickMediaServer();
        String transport = support.normalizeTransport(req.getTransport());
        String streamId = req.getDeviceId() + "_" + req.getChannelId();

        String ssrc = ssrcFactory.acquire(server.getServerId(), false);
        int rtpPort;
        try {
            int tcpMode = SdpBuilder.TRANSPORT_TCP_PASSIVE.equalsIgnoreCase(transport) ? 1 : 0;
            rtpPort = support.openRtpServerWithRetry(server, streamId, ssrc, tcpMode);
        } catch (Exception e) {
            ssrcFactory.release(server.getServerId(), ssrc);
            throw new ServiceException("申请 RTP 收流端口失败: " + e.getMessage());
        }

        CallIdHeader callIdHeader = headerProvider.newCallId(device);
        if (callIdHeader == null) {
            zlmRestClient.closeRtpServer(server, streamId);
            ssrcFactory.release(server.getServerId(), ssrc);
            throw new ServiceException("SIP Provider 未就绪");
        }
        String callId = callIdHeader.getCallId();
        String fromTag = SipUtils.newFromTag();

        GbInviteSession session = new GbInviteSession();
        session.setStreamId(streamId);
        session.setServerId(server.getServerId());
        session.setDeviceId(req.getDeviceId());
        session.setChannelId(req.getChannelId());
        session.setSsrc(ssrc);
        session.setRtpPort(rtpPort);
        session.setTransport(transport);
        session.setCallId(callId);
        session.setFromTag(fromTag);
        session.setSessionType(GbInviteSession.TYPE_PLAY);
        session.setStatus(GbInviteSession.STATUS_INVITING);
        session.setStartTime(LocalDateTime.now());
        session.setRecord(mediaProperties.isRecordSip());
        inviteSessionService.save(session);

        CompletableFuture<PlayResultResponse> future = new CompletableFuture<>();
        pending.put(streamId, future);

        try {
            // SDP c= 行必须是设备能直推 RTP 到的 ZLM 地址（streamIp 优先，回退 hostIp），
            // 跟 solo 自己的 SIP 监听 IP 无关。否则设备照 SDP 推到 solo 上，ZLM 永远收不到包。
            String sdpIp = server.getStreamIp() != null && !server.getStreamIp().isBlank()
                    ? server.getStreamIp() : server.getHostIp();
            String sdpBody = SdpBuilder.buildPlay(sipProperties.getId(), sdpIp, rtpPort, ssrc, transport);
            long inviteTimeoutMs = Math.max(5_000L, mediaProperties.getInviteTimeoutSeconds() * 1000L);

            sipCommander.sendInvite(device, req.getChannelId(), sdpBody, ssrc, callIdHeader, fromTag,
                    okEv -> log.debug("[实时点播] 收到 INVITE 2xx stream={}", streamId),
                    errEv -> {
                        int code = errEv == null || errEv.getResponse() == null
                                ? -1 : errEv.getResponse().getStatusCode();
                        support.failSession(session, LOG_TAG, "INVITE 失败 status=" + code);
                        CompletableFuture<PlayResultResponse> f = pending.remove(streamId);
                        if (f != null) {
                            f.completeExceptionally(new ServiceException("INVITE 失败 status=" + code));
                        }
                    },
                    inviteTimeoutMs);
        } catch (Exception e) {
            pending.remove(streamId);
            support.failSession(session, LOG_TAG, "INVITE 异常: " + e.getMessage());
            throw new ServiceException("INVITE 异常: " + e.getMessage());
        }

        // 同步等流就绪
        try {
            return future.get(mediaProperties.getInviteTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pending.remove(streamId);
            support.timeoutSession(session, LOG_TAG, "等待流就绪超时");
            throw new ServiceException("点播超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.remove(streamId);
            support.failSession(session, LOG_TAG, "等待被中断");
            throw new ServiceException("等待被中断");
        } catch (ExecutionException e) {
            pending.remove(streamId);
            Throwable cause = e.getCause();
            if (cause instanceof ServiceException se) {
                throw se;
            }
            throw new ServiceException(cause == null ? "点播失败" : cause.getMessage());
        }
    }

    @Override
    public void stop(PlayStopRequest req) {
        if (req == null || req.getStreamId() == null) {
            throw new ServiceException("streamId 不能为空");
        }
        GbInviteSession session = inviteSessionService.getByStreamId(req.getStreamId());
        if (session == null) {
            log.info("[实时点播] stop: stream={} 无对应会话（已清扫）", req.getStreamId());
            return;
        }
        if (GbInviteSession.STATUS_STOPPED.equals(session.getStatus())
                || GbInviteSession.STATUS_TIMEOUT.equals(session.getStatus())
                || GbInviteSession.STATUS_FAILED.equals(session.getStatus())) {
            return;
        }

        GbDevice device = support.device(session.getDeviceId());
        if (device != null && session.getCallId() != null && session.getFromTag() != null && session.getToTag() != null) {
            sipCommander.sendBye(device, session);
        } else {
            log.warn("[实时点播] stop 时设备/Dialog 信息不全，跳过 BYE stream={}", session.getStreamId());
        }

        support.cleanupZlm(session);
        inviteSessionService.removeByStreamId(session.getStreamId());

        CompletableFuture<PlayResultResponse> f = pending.remove(session.getStreamId());
        if (f != null && !f.isDone()) {
            f.completeExceptionally(new ServiceException("会话已停止"));
        }
        log.info("[实时点播] stop ok stream={}", session.getStreamId());
    }

    // ============================================================
    //  Hook 钩子
    // ============================================================

    @Override
    public void onInviteOk(String callId, String remoteSsrc) {
        if (callId == null || callId.isBlank()) {
            return;
        }
        GbInviteSession session = inviteSessionService.getByCallId(callId);
        if (session == null) {
            log.warn("[实时点播] onInviteOk 无对应会话 callId={}", callId);
            return;
        }
        if (remoteSsrc == null || remoteSsrc.isBlank() || remoteSsrc.equals(session.getSsrc())) {
            // 设备没回 y= 或 SSRC 一致：不需要任何动作
            return;
        }
        log.warn("[实时点播] SSRC 不一致 local={} remote={} stream={}，尝试改写 ZLM 过滤值",
                session.getSsrc(), remoteSsrc, session.getStreamId());
        MediaServer server = support.mediaServer(session.getServerId());
        if (server == null) {
            log.error("[实时点播] SSRC 自适应失败：节点不存在 serverId={}", session.getServerId());
            support.failAndBye(session, LOG_TAG, "节点丢失 serverId=" + session.getServerId());
            failPending(session.getStreamId(), "节点丢失");
            return;
        }
        boolean ok = zlmRestClient.updateRtpServerSSRC(server, session.getStreamId(), remoteSsrc);
        if (!ok) {
            // ZLM 拒绝改写：发 BYE 让设备停推；后续流自然不会上来 → on_rtp_server_timeout 兜底
            log.error("[实时点播] ZLM updateRtpServerSSRC 失败，主动 BYE stream={}", session.getStreamId());
            support.failAndBye(session, LOG_TAG, "ZLM updateRtpServerSSRC 失败");
            failPending(session.getStreamId(), "ZLM updateRtpServerSSRC 失败");
            return;
        }
        // ZLM 已切；释放本地 SSRC、把 session 改成设备 SSRC，保持库内与现网一致
        String oldSsrc = session.getSsrc();
        ssrcFactory.release(session.getServerId(), oldSsrc);
        session.setSsrc(remoteSsrc);
        inviteSessionService.update(session);
        log.info("[实时点播] SSRC 自适应成功 stream={} {} → {}",
                session.getStreamId(), oldSsrc, remoteSsrc);
    }

    @Override
    public void onStreamReady(String app, String stream, String mediaServerId) {
        if (!APP.equals(app)) {
            return; // 不是 GB28181 拉的流
        }
        GbInviteSession session = inviteSessionService.getByStreamId(stream);
        if (session == null) {
            log.warn("[实时点播] on_stream_changed 找不到会话 stream={}", stream);
            return;
        }
        if (GbInviteSession.STATUS_READY.equals(session.getStatus())) {
            return; // 幂等
        }
        MediaServer server = support.mediaServer(session.getServerId());
        if (server == null) {
            log.error("[实时点播] on_stream_changed 节点丢失 serverId={}", session.getServerId());
            return;
        }

        // 回填播放 URL
        session.setStatus(GbInviteSession.STATUS_READY);
        session.setReadyTime(LocalDateTime.now());
        session.setFlvUrl(urlBuilder.buildFlv(server, APP, stream));
        session.setWsFlvUrl(urlBuilder.buildWsFlv(server, APP, stream));
        session.setHlsUrl(urlBuilder.buildHls(server, APP, stream));
        session.setRtcUrl(urlBuilder.buildRtc(server, APP, stream));
        inviteSessionService.update(session);

        // 回填通道 streamId
        GbDeviceChannel channel = support.channel(session.getDeviceId(), session.getChannelId());
        if (channel != null) {
            channel.setStreamId(stream);
            channelService.updateById(channel);
        }

        log.info("[实时点播] 流就绪 stream={} wsFlv={}", stream, session.getWsFlvUrl());

        // 唤醒等待中的 future
        CompletableFuture<PlayResultResponse> f = pending.remove(stream);
        if (f != null) {
            f.complete(toResult(session));
        }
    }

    @Override
    public void onStreamStopped(String app, String stream, String mediaServerId) {
        if (!APP.equals(app)) {
            return;
        }
        GbInviteSession session = inviteSessionService.getByStreamId(stream);
        if (session == null) {
            return;
        }
        if (GbInviteSession.STATUS_STOPPED.equals(session.getStatus())
                || GbInviteSession.STATUS_FAILED.equals(session.getStatus())
                || GbInviteSession.STATUS_TIMEOUT.equals(session.getStatus())) {
            return;
        }
        // ZLM 端流断了，理论上需要发 BYE 通知设备停推
        GbDevice device = support.device(session.getDeviceId());
        if (device != null && session.getToTag() != null) {
            sipCommander.sendBye(device, session);
        }
        support.cleanupZlm(session);
        inviteSessionService.removeByStreamId(session.getStreamId());
        log.info("[实时点播] 流断开清扫完成 stream={}", stream);
    }

    @Override
    public void onRtpServerTimeout(String streamId) {
        if (streamId == null || streamId.isBlank()) {
            return;
        }
        GbInviteSession session = inviteSessionService.getByStreamId(streamId);
        if (session == null) {
            return;
        }
        if (GbInviteSession.STATUS_STOPPED.equals(session.getStatus())
                || GbInviteSession.STATUS_TIMEOUT.equals(session.getStatus())) {
            return;
        }
        GbDevice device = support.device(session.getDeviceId());
        if (device != null && session.getToTag() != null) {
            sipCommander.sendBye(device, session);
        }
        // ZLM 这边端口本来就超时了，不需要再 closeRtpServer
        ssrcFactory.release(session.getServerId(), session.getSsrc());
        inviteSessionService.removeByStreamId(session.getStreamId());

        CompletableFuture<PlayResultResponse> f = pending.remove(streamId);
        if (f != null && !f.isDone()) {
            f.completeExceptionally(new ServiceException("RTP 端口长时间无数据"));
        }
        log.warn("[实时点播] RTP 端口超时清扫完成 stream={}", streamId);
    }

    @Override
    public void onDeviceBye(GbInviteSession session) {
        if (session == null) return;
        support.cleanupZlm(session);
        inviteSessionService.removeByStreamId(session.getStreamId());

        CompletableFuture<PlayResultResponse> f = pending.remove(session.getStreamId());
        if (f != null && !f.isDone()) {
            f.completeExceptionally(new ServiceException("设备主动 BYE"));
        }
    }

    // ============================================================
    //  helpers
    // ============================================================

    // ============================================================
    //  helpers
    // ============================================================

    /**
     * forceCleanup 包了一层 pending future 唤醒（业务侧"残留启动 INVITING 会话"路径）。
     * 其他通用清扫都走 {@link GbInviteSupport}。
     */
    private void forceCleanup(GbInviteSession session) {
        support.forceCleanup(session);
        failPending(session.getStreamId(), "会话被强清");
    }

    /**
     * 唤醒等待 future 报错，避免业务调用方一直等到点播超时
     */
    private void failPending(String streamId, String reason) {
        CompletableFuture<PlayResultResponse> f = pending.remove(streamId);
        if (f != null && !f.isDone()) {
            f.completeExceptionally(new ServiceException(reason));
        }
    }

    private PlayResultResponse toResult(GbInviteSession session) {
        PlayResultResponse vo = new PlayResultResponse();
        vo.setStreamId(session.getStreamId());
        vo.setApp(APP);
        vo.setMediaServerId(session.getServerId());
        vo.setSsrc(session.getSsrc());
        vo.setRtpPort(session.getRtpPort());
        vo.setTransport(session.getTransport());
        vo.setFlvUrl(session.getFlvUrl());
        vo.setWsFlvUrl(session.getWsFlvUrl());
        vo.setHlsUrl(session.getHlsUrl());
        vo.setRtcUrl(session.getRtcUrl());
        return vo;
    }

    @Override
    public void autoStartPlay(String deviceId, String channelId, String serverId) {
        // autoApplyPlay 异步起点播，不阻塞 ZLM hook 回调
        gb28181BizExecutor.execute(() -> {
            try {
                PlayStartRequest req = new PlayStartRequest();
                req.setDeviceId(deviceId);
                req.setChannelId(channelId);
                // 使用默认传输，由 GB 设备决定
                req.setTransport(null);
                this.start(req);
                log.info("[按需拉流] autoApplyPlay 自动发起点播 device={} channel={}", deviceId, channelId);
            } catch (Exception e) {
                log.error("[按需拉流] autoApplyPlay 失败 device={} channel={}", deviceId, channelId, e);
            }
        });
    }
}
