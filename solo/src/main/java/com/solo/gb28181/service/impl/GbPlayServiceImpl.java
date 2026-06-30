package com.solo.gb28181.service.impl;

import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.config.MediaProperties;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.entity.GbMediaServer;
import com.solo.gb28181.media.MediaUrlBuilder;
import com.solo.gb28181.media.client.ZlmRestClient;
import com.solo.gb28181.media.ssrc.SsrcFactory;
import com.solo.gb28181.request.PlaySnapRequest;
import com.solo.gb28181.request.PlayStartRequest;
import com.solo.gb28181.request.PlayStopRequest;
import com.solo.gb28181.response.PlayResultVO;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.service.GbMediaServerService;
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
import org.springframework.transaction.annotation.Transactional;

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
 * 3. update session=FAILED/TIMEOUT/STOPPED 并补 reason
 * 4. 摘除 pending future</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbPlayServiceImpl implements GbPlayService {

    private static final String APP = "rtp";

    private final Gb28181Properties sipProperties;
    private final MediaProperties mediaProperties;
    private final SipLayer sipLayer;
    private final GbDeviceService deviceService;
    private final GbDeviceChannelService channelService;
    private final GbMediaServerService mediaServerService;
    private final GbInviteSessionService inviteSessionService;
    private final SsrcFactory ssrcFactory;
    private final ZlmRestClient zlmRestClient;
    private final SipCommander sipCommander;
    private final SipRequestHeaderProvider headerProvider;
    private final MediaUrlBuilder urlBuilder;

    /** 等待 on_stream_changed 的 future，key=streamId */
    private final Map<String, CompletableFuture<PlayResultVO>> pending = new ConcurrentHashMap<>();

    // ============================================================
    //  启动清扫：把残留的 INVITING/READY 全部置 FAILED，避免重启后僵尸会话
    // ============================================================

    @PostConstruct
    void cleanupOnStart() {
        List<GbInviteSession> active = inviteSessionService.listAllActive();
        if (active.isEmpty()) {
            return;
        }
        for (GbInviteSession s : active) {
            s.setStatus(GbInviteSession.STATUS_FAILED);
            s.setFailReason("进程重启");
            s.setStopTime(LocalDateTime.now());
            inviteSessionService.updateById(s);
        }
        log.info("[实时点播] 启动清扫：{} 条残留 INVITING/READY 会话置 FAILED", active.size());
    }

    // ============================================================
    //  start / stop / snap
    // ============================================================

    @Override
    public PlayResultVO start(PlayStartRequest req) {
        if (req == null || req.getDeviceId() == null || req.getChannelId() == null) {
            throw new ServiceException("deviceId / channelId 不能为空");
        }

        GbDevice device = deviceService.getByDeviceId(req.getDeviceId());
        if (device == null) {
            throw new ServiceException("设备不存在: " + req.getDeviceId());
        }
        if (device.getStatus() == null || device.getStatus() != 1) {
            throw new ServiceException("设备离线，无法点播");
        }
        GbDeviceChannel channel = channelService.getByDeviceAndChannel(req.getDeviceId(), req.getChannelId());
        if (channel == null) {
            throw new ServiceException("通道不存在: " + req.getChannelId());
        }
        if (!"ON".equalsIgnoreCase(channel.getStatus())) {
            throw new ServiceException("通道离线，无法点播");
        }

        // 幂等：已 READY 直接返回；INVITING 则拒绝避免并发起多个 INVITE
        List<GbInviteSession> exist = inviteSessionService.listActiveByDeviceAndChannel(
                req.getDeviceId(), req.getChannelId());
        for (GbInviteSession s : exist) {
            if (GbInviteSession.STATUS_READY.equals(s.getStatus())) {
                log.info("[实时点播] 复用已就绪会话 stream={}", s.getStreamId());
                return toResult(s);
            }
            if (GbInviteSession.STATUS_INVITING.equals(s.getStatus())) {
                throw new ServiceException("正在启动中，请稍候");
            }
        }

        GbMediaServer server = pickMediaServer();
        String transport = normalizeTransport(req.getTransport());
        String streamId = req.getDeviceId() + "_" + req.getChannelId();

        String ssrc = ssrcFactory.acquire(server.getServerId(), false);
        int rtpPort;
        try {
            int tcpMode = SdpBuilder.TRANSPORT_TCP_PASSIVE.equalsIgnoreCase(transport) ? 1 : 0;
            rtpPort = openRtpServerWithRetry(server, streamId, ssrc, tcpMode);
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
        inviteSessionService.save(session);

        CompletableFuture<PlayResultVO> future = new CompletableFuture<>();
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
                        failSession(session, "INVITE 失败 status=" + code);
                        CompletableFuture<PlayResultVO> f = pending.remove(streamId);
                        if (f != null) {
                            f.completeExceptionally(new ServiceException("INVITE 失败 status=" + code));
                        }
                    },
                    inviteTimeoutMs);
        } catch (Exception e) {
            pending.remove(streamId);
            failSession(session, "INVITE 异常: " + e.getMessage());
            throw new ServiceException("INVITE 异常: " + e.getMessage());
        }

        // 同步等流就绪
        try {
            return future.get(mediaProperties.getInviteTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pending.remove(streamId);
            timeoutSession(session, "等待流就绪超时");
            throw new ServiceException("点播超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.remove(streamId);
            failSession(session, "等待被中断");
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

        GbDevice device = deviceService.getByDeviceId(session.getDeviceId());
        if (device != null && session.getCallId() != null && session.getFromTag() != null && session.getToTag() != null) {
            sipCommander.sendBye(device, session);
        } else {
            log.warn("[实时点播] stop 时设备/Dialog 信息不全，跳过 BYE stream={}", session.getStreamId());
        }

        cleanupZlm(session);
        session.setStatus(GbInviteSession.STATUS_STOPPED);
        session.setStopTime(LocalDateTime.now());
        inviteSessionService.updateById(session);

        CompletableFuture<PlayResultVO> f = pending.remove(session.getStreamId());
        if (f != null && !f.isDone()) {
            f.completeExceptionally(new ServiceException("会话已停止"));
        }
        log.info("[实时点播] stop ok stream={}", session.getStreamId());
    }

    @Override
    public String snap(PlaySnapRequest req) {
        if (req == null || req.getStreamId() == null) {
            throw new ServiceException("streamId 不能为空");
        }
        GbInviteSession session = inviteSessionService.getByStreamId(req.getStreamId());
        if (session == null) {
            throw new ServiceException("会话不存在: " + req.getStreamId());
        }
        if (!GbInviteSession.STATUS_READY.equals(session.getStatus())) {
            throw new ServiceException("流尚未就绪，无法截图");
        }
        GbMediaServer server = mediaServerService.getByServerId(session.getServerId());
        if (server == null) {
            throw new ServiceException("流媒体节点不存在: " + session.getServerId());
        }
        // ZLM getSnap 用 ZLM 自身的播放地址即可，让 ZLM 本地抓帧
        String sourceUrl = "http://127.0.0.1:" + server.getHttpPort() + "/" + APP + "/" + session.getStreamId() + ".live.flv";
        String localPath = zlmRestClient.getSnap(server, sourceUrl, 10, 30);
        String snapUrl = urlBuilder.buildSnapAccessUrl(server, localPath);
        // 同步更新会话最新截图地址
        session.setSnapUrl(snapUrl);
        inviteSessionService.updateById(session);
        return snapUrl;
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
        GbMediaServer server = mediaServerService.getByServerId(session.getServerId());
        if (server == null) {
            log.error("[实时点播] SSRC 自适应失败：节点不存在 serverId={}", session.getServerId());
            failAndBye(session, "节点丢失 serverId=" + session.getServerId());
            return;
        }
        boolean ok = zlmRestClient.updateRtpServerSSRC(server, session.getStreamId(), remoteSsrc);
        if (!ok) {
            // ZLM 拒绝改写：发 BYE 让设备停推；后续流自然不会上来 → on_rtp_server_timeout 兜底
            log.error("[实时点播] ZLM updateRtpServerSSRC 失败，主动 BYE stream={}", session.getStreamId());
            failAndBye(session, "ZLM updateRtpServerSSRC 失败");
            return;
        }
        // ZLM 已切；释放本地 SSRC、把 session 改成设备 SSRC，保持库内与现网一致
        String oldSsrc = session.getSsrc();
        ssrcFactory.release(session.getServerId(), oldSsrc);
        session.setSsrc(remoteSsrc);
        inviteSessionService.updateById(session);
        log.info("[实时点播] SSRC 自适应成功 stream={} {} → {}",
                session.getStreamId(), oldSsrc, remoteSsrc);
    }

    @Override
    @Transactional
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
        GbMediaServer server = mediaServerService.getByServerId(session.getServerId());
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
        inviteSessionService.updateById(session);

        // 回填通道 streamId
        GbDeviceChannel channel = channelService.getByDeviceAndChannel(session.getDeviceId(), session.getChannelId());
        if (channel != null) {
            channel.setStreamId(stream);
            channelService.updateById(channel);
        }

        log.info("[实时点播] 流就绪 stream={} wsFlv={}", stream, session.getWsFlvUrl());

        // 唤醒等待中的 future
        CompletableFuture<PlayResultVO> f = pending.remove(stream);
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
        GbDevice device = deviceService.getByDeviceId(session.getDeviceId());
        if (device != null && session.getToTag() != null) {
            sipCommander.sendBye(device, session);
        }
        cleanupZlm(session);
        session.setStatus(GbInviteSession.STATUS_STOPPED);
        session.setStopTime(LocalDateTime.now());
        session.setFailReason("ZLM 流断开");
        inviteSessionService.updateById(session);
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
        GbDevice device = deviceService.getByDeviceId(session.getDeviceId());
        if (device != null && session.getToTag() != null) {
            sipCommander.sendBye(device, session);
        }
        // ZLM 这边端口本来就超时了，不需要再 closeRtpServer
        ssrcFactory.release(session.getServerId(), session.getSsrc());
        session.setStatus(GbInviteSession.STATUS_TIMEOUT);
        session.setStopTime(LocalDateTime.now());
        session.setFailReason("RTP 端口长时间无数据");
        inviteSessionService.updateById(session);

        CompletableFuture<PlayResultVO> f = pending.remove(streamId);
        if (f != null && !f.isDone()) {
            f.completeExceptionally(new ServiceException("RTP 端口长时间无数据"));
        }
        log.warn("[实时点播] RTP 端口超时清扫完成 stream={}", streamId);
    }

    @Override
    public void onDeviceBye(GbInviteSession session) {
        if (session == null) return;
        if (GbInviteSession.STATUS_STOPPED.equals(session.getStatus())) {
            return;
        }
        cleanupZlm(session);
        session.setStatus(GbInviteSession.STATUS_STOPPED);
        session.setStopTime(LocalDateTime.now());
        session.setFailReason("设备主动 BYE");
        inviteSessionService.updateById(session);

        CompletableFuture<PlayResultVO> f = pending.remove(session.getStreamId());
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
     * 申请 RTP 端口；若 ZLM 报"stream already exists"（上次会话残留），先关再开一次。
     * <p>残留场景：solo 异常退出 / 网络抖动导致 closeRtpServer 没成功，但 ssrc 池里的
     * 占用位通过启动清扫释放了，下次同 streamId 申请就会撞 ZLM 侧残留。</p>
     */
    private int openRtpServerWithRetry(GbMediaServer server, String streamId, String ssrc, int tcpMode) {
        try {
            return zlmRestClient.openRtpServer(server, streamId, ssrc, tcpMode, 0);
        } catch (ServiceException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (!msg.contains("already exists")) {
                throw e;
            }
            log.warn("[实时点播] ZLM 检测到 stream={} 残留，强制 closeRtpServer 后重试", streamId);
            zlmRestClient.closeRtpServer(server, streamId);
            return zlmRestClient.openRtpServer(server, streamId, ssrc, tcpMode, 0);
        }
    }

    private GbMediaServer pickMediaServer() {        // S2.2：取在线的第一个节点；后续按负载均衡再扩
        List<GbMediaServer> all = mediaServerService.list();
        for (GbMediaServer s : all) {
            if (s.getStatus() != null && s.getStatus() == 1) {
                return s;
            }
        }
        // 没有在线节点时退而求其次：取第一个
        if (!all.isEmpty()) {
            log.warn("[实时点播] 无在线节点，使用首个登记节点 serverId={}", all.get(0).getServerId());
            return all.get(0);
        }
        throw new ServiceException("无可用流媒体节点，请先在管理面登记 ZLM");
    }

    private static String normalizeTransport(String t) {
        if (t == null || t.isBlank()) return SdpBuilder.TRANSPORT_UDP;
        if (SdpBuilder.TRANSPORT_TCP_PASSIVE.equalsIgnoreCase(t)) return SdpBuilder.TRANSPORT_TCP_PASSIVE;
        return SdpBuilder.TRANSPORT_UDP;
    }

    private void cleanupZlm(GbInviteSession session) {
        try {
            GbMediaServer server = mediaServerService.getByServerId(session.getServerId());
            if (server != null) {
                zlmRestClient.closeRtpServer(server, session.getStreamId());
            }
        } catch (Exception e) {
            log.warn("[实时点播] closeRtpServer 异常 stream={} {}", session.getStreamId(), e.getMessage());
        }
        ssrcFactory.release(session.getServerId(), session.getSsrc());
    }

    private void failSession(GbInviteSession session, String reason) {
        cleanupZlm(session);
        session.setStatus(GbInviteSession.STATUS_FAILED);
        session.setStopTime(LocalDateTime.now());
        session.setFailReason(reason);
        inviteSessionService.updateById(session);
        log.warn("[实时点播] 会话失败 stream={} reason={}", session.getStreamId(), reason);
    }

    /**
     * SSRC 自适应失败时主动放弃：发 BYE → 清扫 ZLM/SSRC → 唤醒 future。
     * 与 {@link #failSession} 的区别是先发 BYE（设备已收过 200 OK，必须 BYE 才会停推）。
     */
    private void failAndBye(GbInviteSession session, String reason) {
        GbDevice device = deviceService.getByDeviceId(session.getDeviceId());
        if (device != null && session.getToTag() != null) {
            sipCommander.sendBye(device, session);
        }
        cleanupZlm(session);
        session.setStatus(GbInviteSession.STATUS_FAILED);
        session.setStopTime(LocalDateTime.now());
        session.setFailReason(reason);
        inviteSessionService.updateById(session);

        CompletableFuture<PlayResultVO> f = pending.remove(session.getStreamId());
        if (f != null && !f.isDone()) {
            f.completeExceptionally(new ServiceException(reason));
        }
        log.warn("[实时点播] 会话被动放弃 stream={} reason={}", session.getStreamId(), reason);
    }

    private void timeoutSession(GbInviteSession session, String reason) {
        cleanupZlm(session);
        // 主动尝试发 BYE：设备可能已经回了 200 OK 但流没上来
        GbDevice device = deviceService.getByDeviceId(session.getDeviceId());
        if (device != null && session.getToTag() != null) {
            sipCommander.sendBye(device, session);
        }
        session.setStatus(GbInviteSession.STATUS_TIMEOUT);
        session.setStopTime(LocalDateTime.now());
        session.setFailReason(reason);
        inviteSessionService.updateById(session);
        log.warn("[实时点播] 会话超时 stream={} reason={}", session.getStreamId(), reason);
    }

    private PlayResultVO toResult(GbInviteSession session) {
        PlayResultVO vo = new PlayResultVO();
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
        vo.setSnapUrl(session.getSnapUrl());
        return vo;
    }
}
