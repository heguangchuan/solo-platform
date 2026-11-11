package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.request.PlayStartRequest;
import com.solo.gb28181.request.PlayStopRequest;
import com.solo.gb28181.response.PlayResultResponse;
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
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.properties.MediaProperties;
import com.solo.media.service.MediaServerSelector;
import com.solo.media.ssrc.SsrcFactory;
import com.solo.media.support.ViewActor;
import com.solo.media.support.ViewTokenSigner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sip.header.CallIdHeader;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 实时点播流程：
 *
 * 校验设备、通道、节点，已 READY 的幂等返回；SsrcFactory 申请 SSRC；ZLM openRtpServer 申请收流端口；
 * 会话入库(INVITING，这时 Call-Id / from-tag 已经定了)；注册 CompletableFuture 到 pending；
 * 发 INVITE，ACK 由 InviteResponseProcessor 异步处理；future 同步等 inviteTimeoutSeconds 秒，
 * 等 on_stream_changed 回调或超时。
 *
 * 失败/超时的清扫顺序不能乱：先 closeRtpServer（端口在 ZLM 手里），再 release SSRC，
 * 然后删会话、摘 pending。先关后还，免得新会话拿到同一个 ssrc 时 openRtpServer 被拒。
 * 公共清扫代码在 GbInviteSessionCleanupService，和回放共用。
 *
 * autoApplyPlay 走专属线程池异步点播，不阻塞 ZLM hook 回调。
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
    private final GbDeviceMapper deviceMapper;
    private final GbDeviceChannelMapper channelMapper;
    private final MediaServerSelector mediaServerSelector;
    private final GbMediaServerMapper mediaServerMapper;
    private final ViewTokenSigner viewTokenSigner;

    /**
     * 等待 on_stream_changed 的 future，key=streamId
     */
    private final Map<String, CompletableFuture<PlayResultResponse>> pending = new ConcurrentHashMap<>();

    // ============================================================
    //  start / stop
    // ============================================================

    @Override
    public PlayResultResponse start(PlayStartRequest req) {
        if (req == null || req.getDeviceId() == null || req.getChannelId() == null) {
            throw new ServiceException("deviceId / channelId 不能为空");
        }

        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, req.getDeviceId()));
        if (device == null) {
            throw new ServiceException("设备不存在: " + req.getDeviceId());
        }
        if (device.getStatus() == null || device.getStatus() != 1) {
            throw new ServiceException("设备离线，无法点播");
        }
        GbDeviceChannel channel = channelMapper.selectOne(
                new LambdaQueryWrapper<GbDeviceChannel>()
                        .eq(GbDeviceChannel::getDeviceId, req.getDeviceId())
                        .eq(GbDeviceChannel::getChannelId, req.getChannelId()));
        if (channel == null) {
            throw new ServiceException("通道不存在: " + req.getChannelId());
        }
        if (!"ON".equalsIgnoreCase(channel.getStatus())) {
            throw new ServiceException("通道离线，无法点播");
        }


        String rawToken = rawStreamNumber(req.getStreamNumber());
        String streamNumber = normalizeStreamNumber(req.getStreamNumber());
        log.info("[实时点播] 收到点播请求 设备:{} 通道:{} 码流:{}",
                req.getDeviceId(), req.getChannelId(), streamLabel(streamNumber));
        String streamId = buildStreamId(req.getDeviceId(), req.getChannelId(), streamNumber);

        // 幂等：以 streamId（= 通道 + 码流）为粒度判定，不同码流互不影响
        GbInviteSession existing = inviteSessionService.getByStreamId(streamId);
        if (existing != null) {
            if (GbInviteSession.STATUS_READY.equals(existing.getStatus())) {
                if (streamStillOnline(existing)) {
                    log.info("[实时点播] 复用已就绪会话 流ID:{} 码流:{}", streamId, streamLabel(streamNumber));
                    return withViewToken(toResult(existing));
                }
                // 接管来的会话可能已经死在 ZLM 上（节点重启、流被外部关掉），清掉重拉，
                // 不然前端拿到地址也是黑屏
                inviteCleanup.failAndBye(existing, LOG_TAG, "复用会话时流已不在");
            }
            if (GbInviteSession.STATUS_INVITING.equals(existing.getStatus())) {
                // 同通道同码流正在邀请中，告诉前端稍候，不要重复发起 INVITE 导致重复流
                log.warn("[实时点播] 该通道该码流正在点播邀请中 流ID:{} 原因:请稍后重试", streamId);
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
            String sdpIp = server.getStreamIp() != null && !server.getStreamIp().isBlank()
                    ? server.getStreamIp() : server.getHostIp();
            String sdpBody = SdpBuilder.buildPlay(sipProperties.getId(), sdpIp, rtpPort, ssrc, transport, rawToken);
            long inviteTimeoutMs = Math.max(5_000L, mediaProperties.getInviteTimeoutSeconds() * 1000L);

            sipCommander.sendInvite(device, req.getChannelId(), sdpBody, ssrc, rtpPort, callIdHeader, fromTag,
                    okEv -> log.debug("[实时点播] 收到 INVITE 2xx 流ID:{}", streamId),
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
            return withViewToken(future.get(mediaProperties.getInviteTimeoutSeconds(), TimeUnit.SECONDS));
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
            // 会话没了不代表流没了，顺手看一眼 ZLM，别留下没人管的流
            if (inviteCleanup.closeOrphanStream(req.getStreamId())) {
                log.warn("[实时点播] stop 流ID:{} 无会话但流还在，已兜底关掉", req.getStreamId());
            } else {
                log.info("[实时点播] stop 流ID:{} 无对应会话 原因:已结束", req.getStreamId());
            }
            return;
        }
        if (GbInviteSession.STATUS_STOPPED.equals(session.getStatus())
                || GbInviteSession.STATUS_TIMEOUT.equals(session.getStatus())
                || GbInviteSession.STATUS_FAILED.equals(session.getStatus())) {
            return;
        }

        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, session.getDeviceId()));
        if (device != null && session.getCallId() != null && session.getFromTag() != null && session.getToTag() != null) {
            sipCommander.sendBye(device, session);
        } else {
            log.warn("[实时点播] stop 时设备/Dialog 信息不全 流ID:{} 原因:跳过 BYE", session.getStreamId());
        }

        inviteCleanup.cleanupZlm(session);
        inviteSessionService.removeByStreamId(session.getStreamId());

        CompletableFuture<PlayResultResponse> f = pending.remove(session.getStreamId());
        if (f != null && !f.isDone()) {
            f.completeExceptionally(new ServiceException("会话已停止"));
        }
        log.info("[实时点播] stop 结果:成功 流ID:{}", session.getStreamId());
    }


    @Override
    public void onInviteOk(String callId, String remoteSsrc) {
        if (callId == null || callId.isBlank()) {
            return;
        }
        GbInviteSession session = inviteSessionService.getByCallId(callId);
        if (session == null) {
            log.warn("[实时点播] onInviteOk 无对应会话 Call-ID:{}", callId);
            return;
        }
        // 设备与下级平台不再区分（gb_platform 已翻转为黑名单，source 统一 REGISTER），
        // 日志前缀不再按级联切换
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
        log.info("[{}] 下级自定义 SSRC {} -> {} 流ID:{}", LOG_TAG, oldSsrc, remoteSsrc, session.getStreamId());
    }


    @Override
    public void onStreamReady(String app, String stream, String mediaServerId) {
        if (!APP.equals(app)) {
            return; // 不是 GB28181 拉的流
        }
        GbInviteSession session = inviteSessionService.getByStreamId(stream);
        if (session == null) {
            log.warn("[实时点播] on_stream_changed 找不到会话 流ID:{}", stream);
            return;
        }
        if (GbInviteSession.STATUS_READY.equals(session.getStatus())) {
            return; // 幂等
        }
        MediaServer server = mediaServerMapper.selectOne(
                new LambdaQueryWrapper<MediaServer>().eq(MediaServer::getServerId, session.getServerId()));
        if (server == null) {
            log.error("[实时点播] on_stream_changed 节点丢失 节点ID:{}", session.getServerId());
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

        log.info("[实时点播] 流就绪 流ID:{} 码流:{} WS-FLV:{}",
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
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, session.getDeviceId()));
        if (device != null && session.getToTag() != null) {
            sipCommander.sendBye(device, session);
        }
        inviteCleanup.cleanupZlm(session);
        inviteSessionService.removeByStreamId(session.getStreamId());
        log.info("[实时点播] 流断开清扫完成 流ID:{}", stream);
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
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, session.getDeviceId()));
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
        log.warn("[实时点播] RTP 端口超时清扫完成 流ID:{}", streamId);
    }

    @Override
    public void onDeviceBye(GbInviteSession session) {
        if (session == null) {
            return;
        }
        inviteCleanup.cleanupZlm(session);
        inviteSessionService.removeByStreamId(session.getStreamId());

        CompletableFuture<PlayResultResponse> f = pending.remove(session.getStreamId());
        if (f != null && !f.isDone()) {
            f.completeExceptionally(new ServiceException("设备主动 BYE"));
        }
    }

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
     * 返回前按当前观看者现签。会话上的裸地址多人共享，不能在流就绪时签死；
     * 没有用户上下文的内部调用（级联取流、按需拉流）拿裸地址。
     */
    private PlayResultResponse withViewToken(PlayResultResponse vo) {
        ViewActor actor = viewTokenSigner.currentUserActor();
        if (actor == null) {
            return vo;
        }
        String sid = vo.getStreamId();
        vo.setFlvUrl(viewTokenSigner.appendToken(vo.getFlvUrl(), actor, sid));
        vo.setWsFlvUrl(viewTokenSigner.appendToken(vo.getWsFlvUrl(), actor, sid));
        vo.setHlsUrl(viewTokenSigner.appendToken(vo.getHlsUrl(), actor, sid));
        vo.setRtcUrl(viewTokenSigner.appendToken(vo.getRtcUrl(), actor, sid));
        return vo;
    }


    private static String rawStreamNumber(String streamNumber) {
        return streamNumber == null || streamNumber.isBlank() ? null : streamNumber.trim();
    }


    private static String normalizeStreamNumber(String streamNumber) {
        if (streamNumber == null || streamNumber.isBlank()) {
            return null;
        }
        return streamNumber.trim().replace(":", "_");
    }


    private static String buildStreamId(String deviceId, String channelId, String streamNumber) {
        String base = deviceId + "_" + channelId;
        return streamNumber == null || streamNumber.isBlank() ? base : base + "_" + streamNumber;
    }

    /**
     * 复用 READY 会话前看一眼流还在不在。节点标了离线就不再探，直接当不能复用；
     * 节点在线但探不通就当流还在，别把正常播放误伤成重拉。
     */
    private boolean streamStillOnline(GbInviteSession session) {
        MediaServer server = mediaServerMapper.selectOne(
                new LambdaQueryWrapper<MediaServer>().eq(MediaServer::getServerId, session.getServerId()));
        if (server == null || !Integer.valueOf(1).equals(server.getStatus())) {
            return false;
        }
        Boolean online = zlmRestClient.isMediaOnlineOrNull(server, APP, session.getStreamId());
        return online == null || online;
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
                log.info("[按需拉流] autoApplyPlay 自动发起点播 设备:{} 通道:{}", deviceId, channelId);
            } catch (Exception e) {
                log.error("[按需拉流] autoApplyPlay 失败 设备:{} 通道:{} 错误:{}",
                        deviceId, channelId, e.getMessage(), e);
            }
        });
    }
}
