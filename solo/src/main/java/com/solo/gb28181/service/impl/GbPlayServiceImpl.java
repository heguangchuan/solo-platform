package com.solo.gb28181.service.impl;

import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.request.PlayStartRequest;
import com.solo.gb28181.request.PlayStopRequest;
import com.solo.gb28181.response.PlayResultResponse;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.service.GbInviteSessionCleanupService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.service.GbPlayService;
import com.solo.gb28181.sip.SipLayer;
import com.solo.gb28181.transmit.cmd.SdpBuilder;
import com.solo.gb28181.transmit.cmd.SipCommander;
import com.solo.gb28181.transmit.cmd.SipRequestHeaderProvider;
import com.solo.gb28181.utils.SipUtils;
import com.solo.media.MediaUrlBuilder;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.properties.MediaProperties;
import com.solo.media.service.MediaServerSelector;
import com.solo.media.service.MediaServerService;
import com.solo.media.ssrc.SsrcFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sip.header.CallIdHeader;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

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
 * <p>大量公共清扫代码抽到 {@link GbInviteSessionCleanupService}，与回放共用。</p>
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
    private final GbInviteSessionCleanupService inviteCleanup;
    private final java.util.concurrent.ThreadPoolExecutor gb28181BizExecutor;
    private final GbDeviceService deviceService;
    private final GbDeviceChannelService channelService;
    private final MediaServerSelector mediaServerSelector;
    private final MediaServerService mediaServerService;

    /**
     * 等待 on_stream_changed 的 future，key=streamId
     */
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

        GbDevice device = deviceService.lambdaQuery().eq(GbDevice::getDeviceId, req.getDeviceId()).one();
        if (device == null) {
            throw new ServiceException("设备不存在: " + req.getDeviceId());
        }
        if (device.getStatus() == null || device.getStatus() != 1) {
            throw new ServiceException("设备离线，无法点播");
        }
        GbDeviceChannel channel = channelService.lambdaQuery()
                .eq(GbDeviceChannel::getDeviceId, req.getDeviceId())
                .eq(GbDeviceChannel::getChannelId, req.getChannelId())
                .one();
        if (channel == null) {
            throw new ServiceException("通道不存在: " + req.getChannelId());
        }
        if (!"ON".equalsIgnoreCase(channel.getStatus())) {
            throw new ServiceException("通道离线，无法点播");
        }


        String rawToken = rawStreamNumber(req.getStreamNumber());
        String streamNumber = normalizeStreamNumber(req.getStreamNumber());
        log.info("[实时点播] 收到点播请求 device={} channel={} 码流={}",
                req.getDeviceId(), req.getChannelId(), streamLabel(streamNumber));
        String streamId = buildStreamId(req.getDeviceId(), req.getChannelId(), streamNumber);

        // 幂等：以 streamId（= 通道 + 码流）为粒度判定，不同码流互不影响
        GbInviteSession existing = inviteSessionService.getByStreamId(streamId);
        if (existing != null) {
            if (GbInviteSession.STATUS_READY.equals(existing.getStatus())) {
                log.info("[实时点播] 复用已就绪会话 stream={} 码流={}", streamId, streamLabel(streamNumber));
                return toResult(existing);
            }
            if (GbInviteSession.STATUS_INVITING.equals(existing.getStatus())) {
                // 同通道同码流正在邀请中，告诉前端稍候，不要重复发起 INVITE 导致重复流
                log.warn("[实时点播] 该通道该码流正在点播邀请中，请稍后重试 stream={}", streamId);
                throw new ServiceException("该通道正在请求点播，请稍后重试");
            }
        }

        MediaServer server = mediaServerSelector.pick();
        String transport = SdpBuilder.normalizeTransport(req.getTransport());

        String ssrc = ssrcFactory.acquire(server.getServerId(), false);
        int rtpPort;
        try {
            int tcpMode = transportToTcpMode(transport);
            rtpPort = inviteCleanup.openRtpServerWithRetry(server, streamId, tcpMode);
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
        session.setStreamNumber(streamNumber);
        session.setCallId(callId);
        session.setFromTag(fromTag);
        session.setSessionType(GbInviteSession.TYPE_PLAY);
        session.setStatus(GbInviteSession.STATUS_INVITING);
        session.setStartTime(LocalDateTime.now());
        inviteSessionService.save(session);

        CompletableFuture<PlayResultResponse> future = new CompletableFuture<>();
        pending.put(streamId, future);

        try {
            // SDP c= 行必须是设备能直推 RTP 到的 ZLM 地址（streamIp 优先，回退 hostIp），
            // 跟 solo 自己的 SIP 监听 IP 无关。否则设备照 SDP 推到 solo 上，ZLM 永远收不到包。
            String sdpIp = server.getStreamIp() != null && !server.getStreamIp().isBlank()
                    ? server.getStreamIp() : server.getHostIp();
            String sdpBody = SdpBuilder.buildPlay(sipProperties.getId(), sdpIp, rtpPort, ssrc, transport, rawToken);
            long inviteTimeoutMs = Math.max(5_000L, mediaProperties.getInviteTimeoutSeconds() * 1000L);

            sipCommander.sendInvite(device, req.getChannelId(), sdpBody, ssrc, rtpPort, callIdHeader, fromTag,
                    okEv -> log.debug("[实时点播] 收到 INVITE 2xx stream={}", streamId),
                    errEv -> {
                        int code = errEv == null || errEv.getResponse() == null
                                ? -1 : errEv.getResponse().getStatusCode();
                        inviteCleanup.failSession(session, LOG_TAG, "INVITE 失败 status=" + code);
                        CompletableFuture<PlayResultResponse> f = pending.remove(streamId);
                        if (f != null) {
                            f.completeExceptionally(new ServiceException("INVITE 失败 status=" + code));
                        }
                    },
                    inviteTimeoutMs);
        } catch (Exception e) {
            pending.remove(streamId);
            inviteCleanup.failSession(session, LOG_TAG, "INVITE 异常: " + e.getMessage());
            throw new ServiceException("INVITE 异常: " + e.getMessage());
        }

        // 同步等流就绪
        try {
            return future.get(mediaProperties.getInviteTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pending.remove(streamId);
            inviteCleanup.timeoutSession(session, LOG_TAG, "等待流就绪超时");
            throw new ServiceException("点播超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.remove(streamId);
            inviteCleanup.failSession(session, LOG_TAG, "等待被中断");
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

        GbDevice device = deviceService.lambdaQuery().eq(GbDevice::getDeviceId, session.getDeviceId()).one();
        if (device != null && session.getCallId() != null && session.getFromTag() != null && session.getToTag() != null) {
            sipCommander.sendBye(device, session);
        } else {
            log.warn("[实时点播] stop 时设备/Dialog 信息不全，跳过 BYE stream={}", session.getStreamId());
        }

        inviteCleanup.cleanupZlm(session);
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
        // 设备与下级平台不再区分（gb_platform 已翻转为黑名单，source 统一 REGISTER），
        // 日志前缀不再按级联切换
        String tag = LOG_TAG;
        if (remoteSsrc == null || remoteSsrc.isBlank() || remoteSsrc.equals(session.getSsrc())) {
            // 设备没回 y= 或 SSRC 一致：不需要任何动作
            return;
        }
        // 不按 SSRC 过滤收流（多端口模式，ADR-025），下级自定义 SSRC 无需改写 ZLM 过滤值。
        // 只把会话里的 SSRC 更新成对端实际在用的值，保持库内与现网一致（前端展示 / 问题排查靠它）。
        String oldSsrc = session.getSsrc();
        ssrcFactory.release(session.getServerId(), oldSsrc);
        session.setSsrc(remoteSsrc);
        inviteSessionService.update(session);
        log.info("[{}] 下级自定义 SSRC {} -> {} stream={}", tag, oldSsrc, remoteSsrc, session.getStreamId());
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
        MediaServer server = mediaServerService.lambdaQuery().eq(MediaServer::getServerId, session.getServerId()).one();
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

        log.info("[实时点播] 流就绪 stream={} 码流={} wsFlv={}",
                stream, streamLabel(session.getStreamNumber()), session.getWsFlvUrl());

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
        GbDevice device = deviceService.lambdaQuery().eq(GbDevice::getDeviceId, session.getDeviceId()).one();
        if (device != null && session.getToTag() != null) {
            sipCommander.sendBye(device, session);
        }
        inviteCleanup.cleanupZlm(session);
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
        GbDevice device = deviceService.lambdaQuery().eq(GbDevice::getDeviceId, session.getDeviceId()).one();
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
        inviteCleanup.cleanupZlm(session);
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
     * 其他通用清扫都走 {@link GbInviteSessionCleanupService}。
     */
    private void forceCleanup(GbInviteSession session) {
        inviteCleanup.forceCleanup(session);
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
        vo.setStreamNumber(session.getStreamNumber());
        vo.setFlvUrl(session.getFlvUrl());
        vo.setWsFlvUrl(session.getWsFlvUrl());
        vo.setHlsUrl(session.getHlsUrl());
        vo.setRtcUrl(session.getRtcUrl());
        return vo;
    }

    /**
     * 原始码流 token：空白 → null（主码流）；否则仅 trim，**保留冒号原样**。
     * <p>用于写进 SDP 的 {@code a=<token>} 行（如 {@code a=streamnumber:1}）——设备据此选主/子码流。
     * SDP a= 行允许冒号，故这里不做替换。</p>
     */
    private static String rawStreamNumber(String streamNumber) {
        return streamNumber == null || streamNumber.isBlank() ? null : streamNumber.trim();
    }

    /**
     * 归一码流 token：空白 → null（主码流）；否则 trim 并**把所有冒号替换为下划线**。
     * <p>用于 streamId 后缀 / 会话持久化 / 日志——冒号在 ZLM 流名、播放 URL 里非法，故换成下划线
     * （{@code streamnumber:1} → {@code streamnumber_1}）。SDP a= 行则用 {@link #rawStreamNumber} 的带冒号原值。</p>
     */
    private static String normalizeStreamNumber(String streamNumber) {
        if (streamNumber == null || streamNumber.isBlank()) {
            return null;
        }
        return streamNumber.trim().replace(":", "_");
    }

    /**
     * 组装 streamId（= ZLM 流名）。主码流用裸 {@code dev_chan}（向后兼容按需拉流等既有 streamId 约定）；
     * 子码流附加 token 后缀，使主/子在 ZLM 里成为两路独立的流，支持不同人同时看不同码流。
     * <p>token 已由 {@link #normalizeStreamNumber} 归一（冒号换下划线），直接拼接即为合法 ZLM 流名。</p>
     */
    private static String buildStreamId(String deviceId, String channelId, String streamNumber) {
        String base = deviceId + "_" + channelId;
        return streamNumber == null || streamNumber.isBlank() ? base : base + "_" + streamNumber;
    }

    /** 码流类型日志标签 */
    private static String streamLabel(String streamNumber) {
        return streamNumber == null || streamNumber.isBlank() ? "主码流" : "子码流(token=" + streamNumber.trim() + ")";
    }

    /** transport 字符串 → ZLM openRtpServer 的 tcp_mode 参数（0=UDP, 1=TCP-passive, 2=TCP-active） */
    private static int transportToTcpMode(String transport) {
        if (SdpBuilder.TRANSPORT_TCP_PASSIVE.equalsIgnoreCase(transport)) return 1;
        if (SdpBuilder.TRANSPORT_TCP_ACTIVE.equalsIgnoreCase(transport)) return 2;
        return 0;
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
