package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbBroadcastSession;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.request.BroadcastStartRequest;
import com.solo.gb28181.request.BroadcastStopRequest;
import com.solo.gb28181.request.BroadcastWebRtcRequest;
import com.solo.gb28181.response.BroadcastResultResponse;
import com.solo.gb28181.service.GbBroadcastSessionService;
import com.solo.gb28181.sip.SipLayer;
import com.solo.gb28181.sip.SipSender;
import com.solo.gb28181.transmit.cmd.SdpBuilder;
import com.solo.gb28181.transmit.cmd.SipCommander;
import com.solo.gb28181.transmit.cmd.SipRequestHeaderProvider;
import com.solo.gb28181.utils.SipUtils;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.service.MediaServerSelector;
import com.solo.media.ssrc.SsrcFactory;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sip.message.Request;
import javax.sip.message.Response;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 语音广播编排（网页麦克风实时喊话 → 单个设备/通道，单向）。
 *
 * <p>链路：浏览器 getUserMedia 采集 → WebRTC 推进 ZLM（app=broadcast）→ ZLM startSendRtp
 * （use_ps=1 打 PS）→ GB28181 设备放音。</p>
 *
 * <p>信令走 GB/T 28181 标准广播时序：</p>
 * <ol>
 *   <li>{@link #start} 平台给设备发 MESSAGE（Notify/Broadcast，DeviceID=放音通道码），同步等设备回 200；</li>
 *   <li>设备回 200 后主动向平台发 INVITE（m=audio，a=recvonly），由 {@link #onDeviceInvite} 应答 200 OK；</li>
 *   <li>设备 ACK 到达后 {@link #onDeviceAck} 调 startSendRtp 真正推音频；TCP-active（设备来连平台）
 *       则在回 200 前先 startSendRtpPassive 开监听；</li>
 *   <li>{@link #stop} 或设备 BYE（{@link #onDeviceBye}）结束。</li>
 * </ol>
 *
 * <p>媒体腿与 {@link CascadeSendService} 被上级点播的反向推流完全同构，只是方向换成设备，
 * 且没有"向设备取视频流"那条腿，源流由浏览器 WebRTC 直接推上来。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbBroadcastService {

    private static final String LOG_TAG = "语音广播";
    private static final String APP = "broadcast";

    /** Broadcast 通知同步等设备回 200 的超时 */
    private static final long NOTIFY_TIMEOUT_MS = 12_000L;
    /** ACK 后等浏览器把音频推进 ZLM 的最长等待（避免设备 ACK 早于推流时 startSendRtp 找不到流） */
    private static final long WAIT_STREAM_MS = 10_000L;

    private final Gb28181Properties properties;
    private final GbBroadcastSessionService sessionService;
    private final GbDeviceMapper deviceMapper;
    private final GbDeviceChannelMapper channelMapper;
    private final MediaServerSelector mediaServerSelector;
    private final GbMediaServerMapper mediaServerMapper;
    private final SsrcFactory ssrcFactory;
    private final ZlmRestClient zlmRestClient;
    private final SipCommander sipCommander;
    private final SipRequestHeaderProvider headerProvider;
    private final SipLayer sipLayer;
    private final SipSender sipSender;

    // ============================================================
    //  开始 / 停止
    // ============================================================

    /**
     * 开始广播：校验设备通道 → 建会话（WAIT_INVITE）→ 发 Broadcast 通知同步等设备 200。
     * <p>这里只等"设备愿意接收广播"的 200；设备随后的反向 INVITE 由
     * {@link #onDeviceInvite} 异步处理。返回的 rtcPushUrl 给浏览器推 WebRTC 用。</p>
     */
    public BroadcastResultResponse start(BroadcastStartRequest req) {
        if (req == null || req.getDeviceId() == null || req.getChannelId() == null) {
            throw new ServiceException("deviceId / channelId 不能为空");
        }

        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, req.getDeviceId()));
        if (device == null) {
            throw new ServiceException("设备不存在: " + req.getDeviceId());
        }
        if (device.getStatus() == null || device.getStatus() != 1) {
            throw new ServiceException("设备离线，无法广播");
        }
        GbDeviceChannel channel = channelMapper.selectOne(
                new LambdaQueryWrapper<GbDeviceChannel>()
                        .eq(GbDeviceChannel::getDeviceId, req.getDeviceId())
                        .eq(GbDeviceChannel::getChannelId, req.getChannelId()));
        if (channel == null) {
            throw new ServiceException("通道不存在: " + req.getChannelId());
        }
        if (!"ON".equalsIgnoreCase(channel.getStatus())) {
            throw new ServiceException("通道离线，无法广播");
        }

        String streamId = buildStreamId(req.getDeviceId(), req.getChannelId());

        // 幂等：单设备单通道同时只允许一路广播；已有未结束会话直接复用（前端重复点"开始"不重复发通知）
        GbBroadcastSession existing = sessionService.get(streamId);
        if (existing != null && !GbBroadcastSession.STATUS_STOPPED.equals(existing.getStatus())) {
            log.info("[{}] 复用已有广播会话 设备:{} 通道:{} 状态:{}",
                    LOG_TAG, req.getDeviceId(), req.getChannelId(), existing.getStatus());
            MediaServer existServer = mediaServer(existing.getServerId());
            return toResult(existing, existServer);
        }

        MediaServer server = mediaServerSelector.pick();
        if (server.getRtcPort() == null || server.getRtcPort() <= 0) {
            throw new ServiceException("流媒体节点未配置 WebRTC 端口(rtcPort)，无法接收网页麦克风推流");
        }
        String ssrc = ssrcFactory.acquire(server.getServerId(), false);

        GbBroadcastSession session = new GbBroadcastSession();
        session.setBroadcastStreamId(streamId);
        session.setServerId(server.getServerId());
        session.setDeviceId(req.getDeviceId());
        session.setChannelId(req.getChannelId());
        session.setSrcApp(APP);
        session.setSrcStream(streamId);
        session.setSsrc(ssrc);
        session.setStatus(GbBroadcastSession.STATUS_WAIT_INVITE);
        session.setCreateTime(LocalDateTime.now());
        sessionService.save(session);

        // 同步等设备对 Broadcast 通知回 200（设备愿意接收才让前端开推）
        CompletableFuture<Void> notified = new CompletableFuture<>();
        try {
            sipCommander.sendBroadcastNotify(device, req.getChannelId(),
                    okEv -> notified.complete(null),
                    errEv -> {
                        int code = errEv == null || errEv.getResponse() == null
                                ? -1 : errEv.getResponse().getStatusCode();
                        notified.completeExceptionally(new ServiceException("设备拒绝广播 status=" + code));
                    },
                    () -> notified.completeExceptionally(new ServiceException("广播通知超时，设备无响应")),
                    NOTIFY_TIMEOUT_MS);
        } catch (Exception e) {
            abortSession(session, "广播通知发送异常: " + e.getMessage());
            throw new ServiceException("广播通知发送异常: " + e.getMessage());
        }

        try {
            notified.get(NOTIFY_TIMEOUT_MS + 2_000L, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            abortSession(session, "等待设备响应超时");
            throw new ServiceException("广播超时：设备无响应");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            abortSession(session, "等待设备响应被中断");
            throw new ServiceException("等待设备响应被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            abortSession(session, cause == null ? "设备响应失败" : cause.getMessage());
            if (cause instanceof ServiceException se) {
                throw se;
            }
            throw new ServiceException(cause == null ? "广播失败" : cause.getMessage());
        }

        log.info("[{}] 设备已接受广播 设备:{} 通道:{} 流ID:{} 等待设备反向 INVITE",
                LOG_TAG, req.getDeviceId(), req.getChannelId(), streamId);
        return toResult(session, server);
    }

    /**
     * 停止广播：Dialog 已建立则给设备发 BYE，随后停推流、关浏览器源流、释放 SSRC、删会话。
     */
    public void stop(BroadcastStopRequest req) {
        if (req == null || req.getStreamId() == null) {
            throw new ServiceException("streamId 不能为空");
        }
        GbBroadcastSession session = sessionService.get(req.getStreamId());
        if (session == null) {
            log.info("[{}] stop 流ID:{} 无对应会话 原因:已清扫", LOG_TAG, req.getStreamId());
            return;
        }
        // WAIT_INVITE 阶段 Dialog 还没建立，不能发 BYE；已回填 tag 才说明 INVITE 200 已完成
        boolean dialogReady = session.getCallId() != null && session.getFromTag() != null && session.getToTag() != null;
        cleanup(session, dialogReady);
        log.info("[{}] 广播已停止 设备:{} 通道:{} 流ID:{}",
                LOG_TAG, session.getDeviceId(), session.getChannelId(), session.getBroadcastStreamId());
    }

    /**
     * 代理浏览器与 ZLM 的 WebRTC SDP 交换：按 streamId 找到广播会话与所在节点，
     * 服务端带 secret 转发 offer，返回 answer。浏览器既不接触 secret 也没有跨域问题。
     */
    public String exchangeWebRtcSdp(BroadcastWebRtcRequest req) {
        if (req == null || req.getStreamId() == null || req.getSdp() == null || req.getSdp().isBlank()) {
            throw new ServiceException("streamId / sdp 不能为空");
        }
        GbBroadcastSession session = sessionService.get(req.getStreamId());
        if (session == null || GbBroadcastSession.STATUS_STOPPED.equals(session.getStatus())) {
            throw new ServiceException("广播会话不存在或已结束: " + req.getStreamId());
        }
        MediaServer server = mediaServer(session.getServerId());
        if (server == null) {
            throw new ServiceException("流媒体节点已丢失: " + session.getServerId());
        }
        return zlmRestClient.exchangeWebRtcSdp(server, APP, session.getBroadcastStreamId(), "push", req.getSdp());
    }

    // ============================================================
    //  设备侧信令回调（由 SIP Processor 调用）
    // ============================================================

    /**
     * 设备收到通知后反向发来的 INVITE（m=audio）。调用方已回过 100 且在业务线程池执行。
     */
    public void onDeviceInvite(SIPRequest request, GbDevice device, String channelId, String deviceSdp) {
        String callId = ((javax.sip.header.CallIdHeader)
                request.getHeader(javax.sip.header.CallIdHeader.NAME)).getCallId();
        String deviceFromTag = request.getFromHeader().getTag();

        // 设备 SDP：c= 是设备收流 IP，m=audio 端口是设备收流端口
        String dstIp = SdpBuilder.extractConnectionIp(deviceSdp);
        int dstPort = SdpBuilder.extractAudioPort(deviceSdp);
        String transport = SdpBuilder.extractAudioTransport(deviceSdp);
        if (dstIp == null || dstPort <= 0) {
            log.warn("[{}] 设备 INVITE SDP 缺 c=/m=audio 关键字段 设备:{} 目的IP:{} 目的端口:{} 原因:回 400",
                    LOG_TAG, device.getDeviceId(), dstIp, dstPort);
            sendError(request, Response.BAD_REQUEST);
            return;
        }

        // 设备 INVITE 的 Call-ID 由设备生成，通知发出时拿不到，只能按 设备+通道 找 WAIT_INVITE 会话
        GbBroadcastSession session = findWaitingSession(device.getDeviceId(), channelId);
        if (session == null) {
            log.warn("[{}] 收到无对应广播的设备 INVITE 设备:{} 通道:{} Call-ID:{} 原因:回 481",
                    LOG_TAG, device.getDeviceId(), channelId, callId);
            sendError(request, Response.CALL_OR_TRANSACTION_DOES_NOT_EXIST);
            return;
        }

        MediaServer server = mediaServer(session.getServerId());
        if (server == null) {
            log.error("[{}] 设备 INVITE 时节点丢失 节点ID:{}", LOG_TAG, session.getServerId());
            sendError(request, Response.SERVER_INTERNAL_ERROR);
            cleanup(session, false);
            return;
        }

        // 回填 Dialog / 收流目标。注意必须 save：update 只写主体，save 才会建 callId 反查索引
        String localToTag = SipUtils.newToTag();
        session.setCallId(callId);
        session.setFromTag(deviceFromTag);
        session.setToTag(localToTag);
        session.setDstIp(dstIp);
        session.setDstPort(dstPort);
        session.setTransport(transport);

        // TCP-active（设备主动来连平台）：先开被动监听拿到端口写进 SDP；其余模式回 200 时端口给 0，等 ACK 再推
        int sendPort = 0;
        if (session.isDeviceActive()) {
            try {
                if (!waitStreamReady(server, session.getSrcStream())) {
                    log.warn("[{}] 浏览器音频流未就绪，被动监听可能无流可推 流ID:{}",
                            LOG_TAG, session.getSrcStream());
                }
                sendPort = zlmRestClient.startSendRtpPassive(server, APP, session.getSrcStream(), session.getSsrc());
                session.setLocalSendPort(sendPort);
                session.setStatus(GbBroadcastSession.STATUS_SENDING);
                session.setSendTime(LocalDateTime.now());
            } catch (Exception e) {
                log.error("[{}] startSendRtpPassive 失败 流ID:{} 错误:{}",
                        LOG_TAG, session.getSrcStream(), e.getMessage(), e);
                sendError(request, Response.SERVER_INTERNAL_ERROR);
                cleanup(session, false);
                return;
            }
        } else {
            session.setStatus(GbBroadcastSession.STATUS_WAIT_ACK);
        }
        sessionService.save(session);

        String sdpIp = server.getStreamIp() != null && !server.getStreamIp().isBlank()
                ? server.getStreamIp() : server.getHostIp();
        String answer = SdpBuilder.buildBroadcastAnswer(
                properties.getId(), sdpIp, sendPort, session.getSsrc(), transport);
        try {
            Response ok = headerProvider.buildBroadcastInviteOk(device, request, answer, localToTag);
            sipSender.send(request.getLocalAddress().getHostAddress(), ok);
            log.info("[{}] 回 200 OK 设备:{} 通道:{} 流ID:{} 传输方式:{} 待ACK:{}",
                    LOG_TAG, device.getDeviceId(), channelId, session.getSrcStream(),
                    transport, !session.isDeviceActive());
        } catch (Exception e) {
            log.error("[{}] 回 200 OK 失败 Call-ID:{} 错误:{}", LOG_TAG, callId, e.getMessage(), e);
            cleanup(session, false);
        }
    }

    /**
     * 设备 ACK：UDP / TCP-passive 此时才 startSendRtp（TCP-active 已在回 200 时开监听，幂等跳过）。
     */
    public void onDeviceAck(String callId) {
        GbBroadcastSession session = sessionService.getByCallId(callId);
        if (session == null) {
            return;
        }
        if (GbBroadcastSession.STATUS_SENDING.equals(session.getStatus())) {
            // TCP-active 已启动，或重复 ACK
            return;
        }
        MediaServer server = mediaServer(session.getServerId());
        if (server == null) {
            log.error("[{}] ACK 时节点丢失 节点ID:{}", LOG_TAG, session.getServerId());
            cleanup(session, true);
            return;
        }
        try {
            // 设备可能在浏览器推流之前就 ACK：startSendRtp 时流还不存在。先等流上线再推
            if (!waitStreamReady(server, session.getSrcStream())) {
                log.warn("[{}] 设备 ACK 后等不到浏览器音频流 流ID:{} 原因:仍尝试 startSendRtp",
                        LOG_TAG, session.getSrcStream());
            }
            boolean udp = !session.isTcp();
            Integer srcPort = sendPortInRange();
            int localPort = zlmRestClient.startSendRtp(server, APP, session.getSrcStream(),
                    session.getSsrc(), session.getDstIp(), session.getDstPort(), udp, srcPort);
            session.setLocalSendPort(localPort > 0 ? localPort : srcPort);
            session.setStatus(GbBroadcastSession.STATUS_SENDING);
            session.setSendTime(LocalDateTime.now());
            sessionService.update(session);
            log.info("[{}] startSendRtp 已推音频 设备:{} 通道:{} 目的:{}:{} UDP:{}",
                    LOG_TAG, session.getDeviceId(), session.getChannelId(),
                    session.getDstIp(), session.getDstPort(), udp);
        } catch (Exception e) {
            log.error("[{}] startSendRtp 失败 Call-ID:{} 错误:{}", LOG_TAG, callId, e.getMessage(), e);
            cleanup(session, true);
        }
    }

    /**
     * 设备主动 BYE 结束广播（由 ByeRequestProcessor 命中广播会话时调用，200 已由 Processor 回过）。
     */
    public void onDeviceBye(GbBroadcastSession session) {
        if (session == null) return;
        cleanup(session, false);
        log.info("[{}] 设备停播清理完成 设备:{} 通道:{} 流ID:{}",
                LOG_TAG, session.getDeviceId(), session.getChannelId(), session.getSrcStream());
    }

    /**
     * ZLM on_send_rtp_stopped 回调：某路推流被 ZLM 中止（设备断开/异常），清理会话。
     */
    public void onSendRtpStopped(String stream, String ssrc) {
        for (GbBroadcastSession s : sessionService.listBySrcStream(stream)) {
            if (ssrc == null || ssrc.isBlank() || ssrc.equals(s.getSsrc())) {
                cleanup(s, true);
                log.info("[{}] on_send_rtp_stopped 清理 流ID:{} SSRC:{}", LOG_TAG, stream, s.getSsrc());
            }
        }
    }

    /**
     * 扫描超时会话：发了通知设备不发 INVITE（WAIT_INVITE）、回了 200 收不到 ACK（WAIT_ACK），
     * 都按 {@code cascade.sendAckTimeoutMs} 兜底释放。WAIT_INVITE 阶段 Dialog 未建立，不发 BYE。
     * 由 GbHealthScheduledTasks 每 10s 调一次。
     */
    public void sweepAckTimeout() {
        long timeoutMs = properties.getCascade().getSendAckTimeoutMs();
        LocalDateTime deadline = LocalDateTime.now().minusNanos(timeoutMs * 1_000_000L);
        for (GbBroadcastSession s : sessionService.listAllActive()) {
            boolean waitAckTimeout = GbBroadcastSession.STATUS_WAIT_ACK.equals(s.getStatus())
                    && s.getCreateTime() != null && s.getCreateTime().isBefore(deadline);
            boolean waitInviteTimeout = GbBroadcastSession.STATUS_WAIT_INVITE.equals(s.getStatus())
                    && s.getCreateTime() != null && s.getCreateTime().isBefore(deadline);
            if (waitAckTimeout) {
                log.warn("[{}] 等待设备 ACK 超时，释放并 BYE 设备 设备:{} 通道:{}",
                        LOG_TAG, s.getDeviceId(), s.getChannelId());
                cleanup(s, true);
            } else if (waitInviteTimeout) {
                log.warn("[{}] 等待设备反向 INVITE 超时，释放会话 设备:{} 通道:{}",
                        LOG_TAG, s.getDeviceId(), s.getChannelId());
                cleanup(s, false);
            }
        }
    }

    // ============================================================
    //  helpers
    // ============================================================

    /**
     * 统一清理：stopSendRtp → 删会话 → 可选 BYE 设备 → 关浏览器源流 → 释放 SSRC。
     *
     * @param byeDevice 是否需要本平台主动给设备发 BYE（设备主动 BYE / Dialog 未建立时传 false）
     */
    private void cleanup(GbBroadcastSession session, boolean byeDevice) {
        MediaServer server = mediaServer(session.getServerId());

        // 1) 停掉给设备的推流（异常仅 warn，ZLM 侧流没了也不影响后续清理）
        if (server != null) {
            try {
                zlmRestClient.stopSendRtp(server, APP, session.getSrcStream(), session.getSsrc());
            } catch (Exception e) {
                log.warn("[{}] stopSendRtp 异常(忽略) 流ID:{} 错误:{}",
                        LOG_TAG, session.getSrcStream(), e.getMessage(), e);
            }
        }

        // 2) 删会话（含 callId / srcStream / all 反查索引）
        sessionService.remove(session.getBroadcastStreamId());

        // 3) 主动 BYE 设备（ACK 超时 / startSendRtp 失败 / ZLM 停推等本平台主动结束场景）
        if (byeDevice) {
            byeDevice(session);
        }

        // 4) 关掉浏览器推上来的源流，释放 ZLM 资源（hook 已对 broadcast 流不做无人观看回收，这里必须手动关）
        if (server != null) {
            try {
                zlmRestClient.closeStream(server, APP, session.getSrcStream(), true);
            } catch (Exception e) {
                log.warn("[{}] closeStream 异常(忽略) 流ID:{} 错误:{}",
                        LOG_TAG, session.getSrcStream(), e.getMessage(), e);
            }
        }

        // 5) SSRC 归还池子
        if (session.getServerId() != null && session.getSsrc() != null) {
            ssrcFactory.release(session.getServerId(), session.getSsrc());
        }
    }

    private void byeDevice(GbBroadcastSession session) {
        if (session.getCallId() == null || session.getFromTag() == null || session.getToTag() == null) {
            // Dialog 没建立（还在等 INVITE/ACK），没有可 BYE 的对话
            return;
        }
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, session.getDeviceId()));
        if (device == null) {
            log.warn("[{}] BYE 设备时设备已删除 设备ID:{}", LOG_TAG, session.getDeviceId());
            return;
        }
        try {
            Request bye = headerProvider.buildBroadcastByeRequest(
                    device, session.getChannelId(), session.getCallId(),
                    session.getFromTag(), session.getToTag());
            sipSender.send(sipLayer.getLocalIp(device.getLocalIp()), bye);
            log.info("[{}] 已向设备发 BYE 设备:{} 通道:{}", LOG_TAG, device.getDeviceId(), session.getChannelId());
        } catch (Exception e) {
            log.warn("[{}] 向设备发 BYE 失败 设备:{} 错误:{}",
                    LOG_TAG, device.getDeviceId(), e.getMessage(), e);
        }
    }

    /** start 失败时释放会话与 SSRC（此时没有推流、没有 Dialog，不发 BYE、不关流） */
    private void abortSession(GbBroadcastSession session, String reason) {
        sessionService.remove(session.getBroadcastStreamId());
        if (session.getServerId() != null && session.getSsrc() != null) {
            ssrcFactory.release(session.getServerId(), session.getSsrc());
        }
        log.warn("[{}] 广播发起失败，会话已释放 设备:{} 通道:{} 原因:{}",
                LOG_TAG, session.getDeviceId(), session.getChannelId(), reason);
    }

    /** 按 设备+通道 找等待 INVITE 的广播会话（streamId 确定性，直接按它取即可） */
    private GbBroadcastSession findWaitingSession(String deviceId, String channelId) {
        String streamId = buildStreamId(deviceId, channelId);
        GbBroadcastSession session = sessionService.get(streamId);
        if (session == null) {
            return null;
        }
        return GbBroadcastSession.STATUS_WAIT_INVITE.equals(session.getStatus()) ? session : null;
    }

    /** 轮询等浏览器音频流在 ZLM 上线；返回 false=超时仍无流 */
    private boolean waitStreamReady(MediaServer server, String stream) {
        long deadline = System.currentTimeMillis() + WAIT_STREAM_MS;
        while (System.currentTimeMillis() < deadline) {
            if (zlmRestClient.isMediaOnline(server, APP, stream)) {
                return true;
            }
            try {
                Thread.sleep(300L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return zlmRestClient.isMediaOnline(server, APP, stream);
    }

    private MediaServer mediaServer(String serverId) {
        return mediaServerMapper.selectOne(
                new LambdaQueryWrapper<MediaServer>().eq(MediaServer::getServerId, serverId));
    }

    private Integer sendPortInRange() {
        // 复用级联反向推流的本地发送端口段（50000-60000），端口序列计数器广播独立
        return sessionService.acquireSendPort(
                properties.getCascade().getSendRtpPortStart(),
                properties.getCascade().getSendRtpPortEnd());
    }

    /** 广播流名单设备单通道唯一：broadcast_<deviceId>_<channelId> */
    private static String buildStreamId(String deviceId, String channelId) {
        return "broadcast_" + deviceId + "_" + channelId;
    }

    private BroadcastResultResponse toResult(GbBroadcastSession session, MediaServer server) {
        BroadcastResultResponse resp = new BroadcastResultResponse();
        resp.setStreamId(session.getBroadcastStreamId());
        resp.setApp(APP);
        resp.setMediaServerId(session.getServerId());
        if (server == null) {
            throw new ServiceException("流媒体节点已丢失: " + session.getServerId());
        }
        if (server.getRtcPort() == null || server.getRtcPort() <= 0) {
            throw new ServiceException("流媒体节点未配置 WebRTC 端口(rtcPort)");
        }
        String ip = server.getStreamIp() != null && !server.getStreamIp().isBlank()
                ? server.getStreamIp() : server.getHostIp();
        resp.setRtcPushUrl("http://" + ip + ":" + server.getRtcPort()
                + "/index/api/webrtc?app=" + APP + "&stream=" + session.getBroadcastStreamId() + "&type=push");
        return resp;
    }

    private void sendError(SIPRequest request, int code) {
        try {
            Response resp = headerProvider.buildResponse(code, request);
            sipSender.send(request.getLocalAddress().getHostAddress(), resp);
        } catch (Exception e) {
            log.warn("[{}] 回 {} 失败 错误:{}", LOG_TAG, code, e.getMessage(), e);
        }
    }
}
