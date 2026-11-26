package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbBroadcastSession;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.mapper.GbParentPlatformMapper;
import com.solo.gb28181.request.BroadcastStartRequest;
import com.solo.gb28181.request.BroadcastStopRequest;
import com.solo.gb28181.request.BroadcastWebRtcRequest;
import com.solo.gb28181.response.BroadcastResultResponse;
import com.solo.gb28181.service.GbBroadcastSessionService;
import com.solo.gb28181.sip.SipLayer;
import com.solo.gb28181.sip.SipSender;
import com.solo.gb28181.transmit.cmd.PlatformCommander;
import com.solo.gb28181.transmit.cmd.SdpBuilder;
import com.solo.gb28181.transmit.cmd.SipCommander;
import com.solo.gb28181.transmit.cmd.SipRequestHeaderProvider;
import com.solo.gb28181.utils.SipUtils;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.service.MediaServerSelector;
import com.solo.media.ssrc.SsrcFactory;
import com.solo.media.support.PlayTarget;
import com.solo.media.support.ViewActor;
import com.solo.media.support.ViewTokenSigner;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sip.message.Request;
import javax.sip.message.Response;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// 平台先发 Broadcast，设备再反向 INVITE，ACK 后由 ZLM 推音频。
// 本地喊话的源是浏览器 WebRTC，级联喊话的源是上级平台。
@Slf4j
@Service
@RequiredArgsConstructor
public class GbBroadcastService {

    private static final String TAG_BROADCAST = "语音播报";
    private static final String TAG_TALK = "语音对讲";
    private static final String APP = "broadcast";
    // 对讲收设备音频的收流 app
    private static final String RECV_APP = "rtp";

    private static final long NOTIFY_TIMEOUT_MS = 12_000L;
    private static final long WAIT_STREAM_MS = 10_000L;
    private static final long WAIT_RECV_MS = 5_000L;

    private final Map<String, CompletableFuture<Void>> readySignals = new ConcurrentHashMap<>();

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
    private final GbParentPlatformMapper platformMapper;
    private final PlatformCommander platformCommander;
    private final ViewTokenSigner viewTokenSigner;



    public BroadcastResultResponse start(BroadcastStartRequest req) {
        if (req == null || req.getDeviceId() == null || req.getChannelId() == null) {
            throw new ServiceException("deviceId / channelId 不能为空");
        }
        MediaServer server = mediaServerSelector.pick();
        if (server.getRtcPort() == null || server.getRtcPort() <= 0) {
            throw new ServiceException("流媒体节点未配置 WebRTC 端口(rtcPort)，无法接收网页麦克风推流");
        }
        String streamId = buildStreamId(req.getDeviceId(), req.getChannelId());
        return doStart(req.getDeviceId(), req.getChannelId(), req.getMode(),
                streamId, APP, streamId, server, true);
    }

    public void startCascadeDeviceLeg(GbBroadcastSession prepared) {
        GbBroadcastSession session = sessionService.get(prepared.getBroadcastStreamId());
        if (session == null || GbBroadcastSession.STATUS_STOPPED.equals(session.getStatus())) {
            throw new ServiceException("级联喊话会话已释放: " + prepared.getBroadcastStreamId());
        }
        if (!GbBroadcastSession.STATUS_WAIT_INVITE.equals(session.getStatus())) {
            log.info("[{}] 设备腿已在进行，跳过重复通知 流ID:{} 状态:{}",
                    tag(session), session.getBroadcastStreamId(), session.getStatus());
            return;
        }
        String action = session.isTalk() ? "对讲" : "播报";
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, session.getDeviceId()));
        if (device == null) {
            throw new ServiceException("设备不存在: " + session.getDeviceId());
        }
        if (device.getStatus() == null || device.getStatus() != 1) {
            throw new ServiceException("设备离线，无法" + action);
        }
        GbDeviceChannel channel = channelMapper.selectOne(
                new LambdaQueryWrapper<GbDeviceChannel>()
                        .eq(GbDeviceChannel::getDeviceId, session.getDeviceId())
                        .eq(GbDeviceChannel::getChannelId, session.getChannelId()));
        if (channel == null) {
            throw new ServiceException("通道不存在: " + session.getChannelId());
        }
        if (!"ON".equalsIgnoreCase(channel.getStatus())) {
            throw new ServiceException("通道离线，无法" + action);
        }

        notifyDeviceAndWait(device, session);
        log.info("[{}] 设备已接受级联喊话 设备:{} 通道:{} 流ID:{} 等待设备反向 INVITE",
                tag(session), session.getDeviceId(), session.getChannelId(), session.getBroadcastStreamId());
    }

    private BroadcastResultResponse doStart(String deviceId, String channelId, String mode,
                                           String broadcastStreamId, String srcApp, String srcStream,
                                           MediaServer server, boolean requireBrowser) {
        String action = GbBroadcastSession.MODE_TALK.equalsIgnoreCase(mode) ? "对讲" : "播报";
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, deviceId));
        if (device == null) {
            throw new ServiceException("设备不存在: " + deviceId);
        }
        if (device.getStatus() == null || device.getStatus() != 1) {
            throw new ServiceException("设备离线，无法" + action);
        }
        GbDeviceChannel channel = channelMapper.selectOne(
                new LambdaQueryWrapper<GbDeviceChannel>()
                        .eq(GbDeviceChannel::getDeviceId, deviceId)
                        .eq(GbDeviceChannel::getChannelId, channelId));
        if (channel == null) {
            throw new ServiceException("通道不存在: " + channelId);
        }
        if (!"ON".equalsIgnoreCase(channel.getStatus())) {
            throw new ServiceException("通道离线，无法" + action);
        }

        GbBroadcastSession existing = sessionService.get(broadcastStreamId);
        if (existing != null && !GbBroadcastSession.STATUS_STOPPED.equals(existing.getStatus())) {
            log.info("[{}] 复用已有会话 设备:{} 通道:{} 状态:{}",
                    tag(existing), deviceId, channelId, existing.getStatus());
            return requireBrowser ? toResult(existing, server) : minimalResult(existing);
        }

        String ssrc = ssrcFactory.acquire(server.getServerId(), false);

        GbBroadcastSession session = new GbBroadcastSession();
        session.setBroadcastStreamId(broadcastStreamId);
        session.setServerId(server.getServerId());
        session.setDeviceId(deviceId);
        session.setChannelId(channelId);
        session.setSrcApp(srcApp);
        session.setSrcStream(srcStream);
        session.setSsrc(ssrc);
        String realMode = (mode == null || mode.isBlank())
                ? GbBroadcastSession.MODE_BROADCAST : mode;
        session.setMode(realMode);
        if (session.isTalk()) {
            session.setRecvStreamId("recv_" + broadcastStreamId);
        }
        session.setStatus(GbBroadcastSession.STATUS_WAIT_INVITE);
        session.setCreateTime(LocalDateTime.now());
        sessionService.save(session);

        try {
            notifyDeviceAndWait(device, session);
        } catch (ServiceException e) {
            abortSession(session, e.getMessage());
            throw e;
        }

        log.info("[{}] 设备已接受{}通知 设备:{} 通道:{} 流ID:{} 等待设备反向 INVITE",
                tag(session), action, deviceId, channelId, broadcastStreamId);
        return requireBrowser ? toResult(session, server) : minimalResult(session);
    }

    private void notifyDeviceAndWait(GbDevice device, GbBroadcastSession session) {
        String action = session.isTalk() ? "对讲" : "播报";
        CompletableFuture<Void> notified = new CompletableFuture<>();
        try {
            sipCommander.sendBroadcastNotify(device, session.getChannelId(), session.isTalk(),
                    okEv -> notified.complete(null),
                    errEv -> {
                        int code = errEv == null || errEv.getResponse() == null
                                ? -1 : errEv.getResponse().getStatusCode();
                        notified.completeExceptionally(new ServiceException("设备拒绝" + action + " status=" + code));
                    },
                    () -> notified.completeExceptionally(new ServiceException(action + "通知超时，设备无响应")),
                    NOTIFY_TIMEOUT_MS);
        } catch (Exception e) {
            throw new ServiceException(action + "通知发送异常: " + e.getMessage());
        }

        try {
            notified.get(NOTIFY_TIMEOUT_MS + 2_000L, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new ServiceException(action + "超时：设备无响应");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("等待设备响应被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ServiceException se) {
                throw se;
            }
            throw new ServiceException(cause == null ? action + "失败" : cause.getMessage());
        }
    }

    private BroadcastResultResponse minimalResult(GbBroadcastSession session) {
        BroadcastResultResponse r = new BroadcastResultResponse();
        r.setStreamId(session.getBroadcastStreamId());
        r.setApp(session.getSrcApp());
        r.setMediaServerId(session.getServerId());
        return r;
    }

    public void stop(BroadcastStopRequest req) {
        if (req == null || req.getStreamId() == null) {
            throw new ServiceException("streamId 不能为空");
        }
        GbBroadcastSession session = sessionService.get(req.getStreamId());
        if (session == null) {
            log.info("[{}] stop 流ID:{} 无对应会话 原因:已清扫", TAG_BROADCAST, req.getStreamId());
            return;
        }
        // tag 未回填说明 INVITE 200 未完成，Dialog 不存在
        boolean dialogReady = session.getCallId() != null && session.getFromTag() != null && session.getToTag() != null;
        cleanup(session, dialogReady, dialogReady);
        log.info("[{}] 已停止 设备:{} 通道:{} 流ID:{}",
                tag(session), session.getDeviceId(), session.getChannelId(), session.getBroadcastStreamId());
    }

    public String exchangeWebRtcSdp(BroadcastWebRtcRequest req) {
        if (req == null || req.getStreamId() == null || req.getSdp() == null || req.getSdp().isBlank()) {
            throw new ServiceException("streamId / sdp 不能为空");
        }
        GbBroadcastSession session = sessionService.get(req.getStreamId());
        if (session == null || GbBroadcastSession.STATUS_STOPPED.equals(session.getStatus())) {
            throw new ServiceException("会话不存在或已结束: " + req.getStreamId());
        }
        MediaServer server = mediaServer(session.getServerId());
        if (server == null) {
            throw new ServiceException("流媒体节点已丢失: " + session.getServerId());
        }
        return zlmRestClient.exchangeWebRtcSdp(server, APP, session.getBroadcastStreamId(), "push", req.getSdp());
    }

    public String exchangeRecvWebRtcSdp(BroadcastWebRtcRequest req) {
        if (req == null || req.getStreamId() == null || req.getSdp() == null || req.getSdp().isBlank()) {
            throw new ServiceException("streamId / sdp 不能为空");
        }
        GbBroadcastSession session = sessionService.get(req.getStreamId());
        if (session == null || GbBroadcastSession.STATUS_STOPPED.equals(session.getStatus())) {
            throw new ServiceException("会话不存在或已结束: " + req.getStreamId());
        }
        if (!session.isTalk()) {
            throw new ServiceException("仅对讲模式支持拉取设备端音频: " + req.getStreamId());
        }
        // 设备反向 INVITE 是异步处理的，recvServerId/recvStreamId 在回 200 OK 前才回填；
        // 浏览器播放腿可能先于该回填到达，这里轮询等待，避免误报“流媒体节点已丢失: null”
        long deadline = System.currentTimeMillis() + WAIT_RECV_MS;
        while (session.getRecvServerId() == null && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ServiceException("等待对讲收流就绪被中断: " + req.getStreamId());
            }
            session = sessionService.get(req.getStreamId());
            if (session == null || GbBroadcastSession.STATUS_STOPPED.equals(session.getStatus())) {
                throw new ServiceException("会话不存在或已结束: " + req.getStreamId());
            }
            // 设备 INVITE 已处理为发送态但不会有收流端口（recvonly 设备，对讲退化为喊话），
            // 不必等满 5s，立即由下方判断返回明确提示
            if (GbBroadcastSession.STATUS_SENDING.equals(session.getStatus())) {
                break;
            }
        }
        if (session.getRecvStreamId() == null || session.getRecvServerId() == null) {
            // 设备 INVITE 已处理完（发送腿已建立）却没有收流端口：设备 offer 为 recvonly 且不上行，
            // 对讲已退化为单向喊话，给前端明确提示而不是笼统的“尚未就绪”
            if (GbBroadcastSession.STATUS_SENDING.equals(session.getStatus())) {
                throw new ServiceException("该设备不支持上行音频（无麦克风/未开启语音对讲），已按语音播报工作");
            }
            throw new ServiceException("对讲反向收流尚未就绪: " + req.getStreamId());
        }
        MediaServer server = mediaServer(session.getRecvServerId());
        if (server == null) {
            throw new ServiceException("流媒体节点已丢失: " + session.getRecvServerId());
        }
        // 播放腿可能早于设备音频到达，等回传流上线再交换，否则 ZLM play 找不到流
        if (!waitStreamReady(server, RECV_APP, session.getRecvStreamId())) {
            throw new ServiceException("设备端音频尚未到达，无法建立对讲收听: " + req.getStreamId());
        }
        String playToken = viewTokenSigner.issueToken(viewTokenSigner.requireCurrentUserActor(),
                PlayTarget.rtc(session.getRecvServerId(), RECV_APP, session.getRecvStreamId()));
        return zlmRestClient.exchangeWebRtcSdp(server, RECV_APP, session.getRecvStreamId(), "play",
                req.getSdp(), playToken);
    }

    public boolean ownsRecvStream(String app, String stream) {
        if (!RECV_APP.equals(app) || stream == null) {
            return false;
        }
        for (GbBroadcastSession s : sessionService.listAllActive()) {
            if (stream.equals(s.getRecvStreamId())) {
                return true;
            }
        }
        return false;
    }



    public void onDeviceInvite(SIPRequest request, GbDevice device, String channelId, String deviceSdp) {
        String callId = ((javax.sip.header.CallIdHeader)
                request.getHeader(javax.sip.header.CallIdHeader.NAME)).getCallId();
        String deviceFromTag = request.getFromHeader().getTag();

        String dstIp = SdpBuilder.extractConnectionIp(deviceSdp);
        int dstPort = SdpBuilder.extractAudioPort(deviceSdp);
        String transport = SdpBuilder.extractAudioTransport(deviceSdp);
        // Call-ID 由设备生成，只能按 设备+通道 找等待中的会话；先找会话以便日志按模式打标题
        GbBroadcastSession session = findWaitingSession(device.getDeviceId(), channelId);
        String logTag = session != null ? tag(session) : TAG_BROADCAST;
        log.info("[{}] 收到设备反向 INVITE 设备:{} 传输:{} 收流地址:{}:{}",
                logTag, device.getDeviceId(), transport, dstIp, dstPort);
        // 排查对讲无回传音频：打出设备 SDP 的 m=audio / a= 行，确认载荷与方向语义
        if (deviceSdp != null) {
            for (String line : deviceSdp.split("\\r?\\n")) {
                String l = line.trim();
                if (l.startsWith("m=audio") || l.startsWith("a=") || l.startsWith("y=") || l.startsWith("c=")) {
                    log.info("[{}] 设备 INVITE SDP > {}", logTag, l);
                }
            }
        }
        if (dstIp == null || dstPort <= 0) {
            log.warn("[{}] 设备 INVITE SDP 缺 c=/m=audio 关键字段 设备:{} 目的IP:{} 目的端口:{} 原因:回 400",
                    logTag, device.getDeviceId(), dstIp, dstPort);
            sendError(request, Response.BAD_REQUEST, logTag);
            return;
        }

        if (session == null) {
            log.warn("[{}] 收到无对应会话的设备 INVITE 设备:{} 通道:{} Call-ID:{} 原因:回 481",
                    logTag, device.getDeviceId(), channelId, callId);
            sendError(request, Response.CALL_OR_TRANSACTION_DOES_NOT_EXIST, logTag);
            return;
        }

        MediaServer server = mediaServer(session.getServerId());
        if (server == null) {
            log.error("[{}] 设备 INVITE 时节点丢失 节点ID:{}", logTag, session.getServerId());
            sendError(request, Response.SERVER_INTERNAL_ERROR, logTag);
            cleanup(session, false, true);
            return;
        }

        String localToTag = SipUtils.newToTag();
        session.setCallId(callId);
        session.setFromTag(deviceFromTag);
        session.setToTag(localToTag);
        session.setDstIp(dstIp);
        session.setDstPort(dstPort);
        session.setTransport(transport);

        int audioPt = SdpBuilder.selectAudioPayload(deviceSdp);
        session.setAudioPt(audioPt);
        String deviceSsrc = SdpBuilder.extractSsrc(deviceSdp);
        if (deviceSsrc != null && !deviceSsrc.isBlank() && !deviceSsrc.equals(session.getSsrc())) {
            String allocated = session.getSsrc();
            session.setSsrc(deviceSsrc);
            ssrcFactory.release(session.getServerId(), allocated);
            log.info("[{}] 采用设备 SDP 的 y={} 作为转推 SSRC，已释放本平台分配的 {}",
                    logTag, deviceSsrc, allocated);
        }

        // 对讲应答方向：海康/大华 IPC 的广播 INVITE 习惯写 a=recvonly，但期望平台回 a=sendrecv，
        // 随后设备会向应答 m= 端口推上行音频（业界平台通行的非标准约定）。因此对讲模式下
        // recvonly / sendrecv offer 一律应答 sendrecv；仅 sendonly/inactive 按 RFC 协商。
        String offeredDir = SdpBuilder.extractAudioDirection(deviceSdp);
        String answerDir;
        if (session.isTalk()) {
            answerDir = switch (offeredDir) {
                case "sendonly" -> "recvonly";
                case "inactive" -> "inactive";
                default -> "sendrecv";
            };
        } else {
            answerDir = "sendonly";
        }
        boolean uplink = "sendrecv".equals(answerDir) || "recvonly".equals(answerDir);

        // 回 200 前必须先拿到本平台发流端口写进 m=audio。踩过的坑：UDP 腿原先等 ACK 后才 startSendRtp，
        // 200 OK 里 m=audio 端口只能填 0，而端口 0 在 SDP 里表示媒体被禁用，海康直接不播放，
        // 结果 RTP 包一直发到设备也没有声音。TCP-passive 除外，端口 0 表示由主动方连接，符合 RFC4145。
        int sendPort = 0;
        boolean usePs = audioPt == SdpBuilder.PT_PS;
        if (session.isDeviceActive()) {
            try {
                if (!waitStreamReady(server, session.getSrcApp(), session.getSrcStream())) {
                    log.warn("[{}] 浏览器音频流未就绪，被动监听可能无流可推 流ID:{}",
                            logTag, session.getSrcStream());
                }
                sendPort = zlmRestClient.startSendRtpPassive(server, session.getSrcApp(),
                        session.getSrcStream(), session.getSsrc(),
                        audioPt, usePs, !usePs);
                session.setLocalSendPort(sendPort);
                session.setStatus(GbBroadcastSession.STATUS_SENDING);
                session.setSendTime(LocalDateTime.now());
            } catch (Exception e) {
                log.error("[{}] startSendRtpPassive 失败 流ID:{} 错误:{}",
                        logTag, session.getSrcStream(), e.getMessage(), e);
                sendError(request, Response.SERVER_INTERNAL_ERROR, logTag);
                cleanup(session, false, true);
                return;
            }
        } else if (!session.isTcp()) {
            // UDP：下行用 startSendRtp 主动发到设备 c=/m= 地址，不依赖设备先推上行包，
            // 避免“双方都在等对方第一个包”的死锁（设备 offer 为 recvonly 时必现）。
            // 先等浏览器音频流上线再建发送器，拿到 ZLM 实际本地端口写进 SDP。
            try {
                if (!waitStreamReady(server, session.getSrcApp(), session.getSrcStream())) {
                    log.warn("[{}] 浏览器音频流未就绪即建立 RTP 发送器，流上线后 ZLM 自动补发 流ID:{}",
                            logTag, session.getSrcStream());
                }
                int srcPort = sendPortInRange();
                int localPort = zlmRestClient.startSendRtp(server, session.getSrcApp(), session.getSrcStream(),
                        session.getSsrc(), dstIp, dstPort, true, srcPort,
                        audioPt, usePs, !usePs);
                sendPort = localPort > 0 ? localPort : srcPort;
                session.setLocalSendPort(sendPort);
                session.setStatus(GbBroadcastSession.STATUS_SENDING);
                session.setSendTime(LocalDateTime.now());
            } catch (Exception e) {
                log.error("[{}] UDP 提前建立 RTP 发送器失败 流ID:{} 错误:{}",
                        logTag, session.getSrcStream(), e.getMessage(), e);
                sendError(request, Response.SERVER_INTERNAL_ERROR, logTag);
                cleanup(session, false, true);
                return;
            }
        } else {
            session.setStatus(GbBroadcastSession.STATUS_WAIT_ACK);
        }
        sessionService.save(session);

        String sdpIp = server.getStreamIp() != null && !server.getStreamIp().isBlank()
                ? server.getStreamIp() : server.getHostIp();
        String answer;
        if (session.isTalk() && uplink) {
            // 对讲双向：应答 m=audio 填平台“收设备上行”的端口（非对称 RTP，与下发源端口不同）。
            // 独立 openRtpServer 收上行，下行 startSendRtp 主动发，收/发两个 UDP 端口互不依赖，
            // 设备收到下行音频即可播，设备上行音频到收流口自然建立收听。
            // 收流端口不过滤 SSRC，但部分海康/大华固件若看到应答 y= 填 0 可能直接拒收/不回传，
            // 因此优先回填设备 offer 中自带的 y= SSRC，没有再用全 0。
            String answerSsrc = "0000000000";
            String deviceOfferedSsrc = SdpBuilder.extractSsrc(deviceSdp);
            if (deviceOfferedSsrc != null && !deviceOfferedSsrc.isBlank()) {
                answerSsrc = deviceOfferedSsrc;
            }
            try {
                int recvPort;
                if (session.getRecvPort() != null && session.getRecvPort() > 0) {
                    recvPort = session.getRecvPort();
                } else {
                    recvPort = zlmRestClient.openRtpServer(server, session.getRecvStreamId(), 0, 0);
                    session.setRecvServerId(server.getServerId());
                    session.setRecvPort(recvPort);
                    sessionService.update(session);
                }
                answer = SdpBuilder.buildTalkAnswer(properties.getId(), sdpIp, recvPort, answerSsrc,
                        transport, audioPt, answerDir);
                log.info("[{}] 对讲协商完成 设备offer:{} 应答:{} 收流端口:{} 下行源端口:{} 流ID:{}",
                        logTag, offeredDir, answerDir, recvPort, sendPort, session.getSrcStream());
            } catch (Exception e) {
                log.error("[{}] 对讲开接收端口失败 流ID:{} 错误:{}",
                        logTag, session.getSrcStream(), e.getMessage(), e);
                sendError(request, Response.SERVER_INTERNAL_ERROR, logTag);
                cleanup(session, false, true);
                return;
            }
        } else {
            if (session.isTalk()) {
                log.warn("[{}] 设备不支持上行音频（offer 方向:{}），对讲退化为单向播报 设备:{} 通道:{}",
                        logTag, offeredDir, session.getDeviceId(), session.getChannelId());
            }
            answer = SdpBuilder.buildBroadcastAnswer(
                    properties.getId(), sdpIp, sendPort, session.getSsrc(), transport, audioPt);
        }
        try {
            Response ok = headerProvider.buildBroadcastInviteOk(device, request, answer, localToTag);
            sipSender.send(request.getLocalAddress().getHostAddress(), ok);
            log.info("[{}] 回 200 OK 设备:{} 通道:{} 流ID:{} 传输方式:{} 待ACK:{}",
                    logTag, device.getDeviceId(), channelId, session.getSrcStream(),
                    transport, !session.isDeviceActive());
        } catch (Exception e) {
            log.error("[{}] 回 200 OK 失败 Call-ID:{} 错误:{}", logTag, callId, e.getMessage(), e);
            // 设备未收到 200，Dialog 未建立，留给超时清扫；上级腿不动
            cleanup(session, false, false);
        }
    }

    public void onDeviceAck(String callId) {
        GbBroadcastSession session = sessionService.getByCallId(callId);
        if (session == null) {
            return;
        }
        if (!callId.equals(session.getCallId())) {
            return;
        }
        log.info("[{}] 收到设备 ACK 设备:{} 流ID:{} 状态:{}",
                tag(session), session.getDeviceId(), session.getBroadcastStreamId(), session.getStatus());
        if (GbBroadcastSession.STATUS_SENDING.equals(session.getStatus())) {
            // UDP/TCP-active 已在回 200 前建好发送器，或收到重复 ACK
            return;
        }
        MediaServer server = mediaServer(session.getServerId());
        if (server == null) {
            log.error("[{}] ACK 时节点丢失 节点ID:{}", tag(session), session.getServerId());
            cleanup(session, true, true);
            return;
        }
        try {
            if (!waitStreamReady(server, session.getSrcApp(), session.getSrcStream())) {
                log.warn("[{}] 设备 ACK 后等不到浏览器音频流 流ID:{} 原因:仍尝试 startSendRtp",
                        tag(session), session.getSrcStream());
            }
            boolean udp = !session.isTcp();
            Integer srcPort = sendPortInRange();
            int pt = session.getAudioPt() != null ? session.getAudioPt() : SdpBuilder.PT_PCMA;
            boolean usePs = pt == SdpBuilder.PT_PS;
            int localPort = zlmRestClient.startSendRtp(server, session.getSrcApp(), session.getSrcStream(),
                    session.getSsrc(), session.getDstIp(), session.getDstPort(), udp, srcPort,
                    pt, usePs, !usePs);
            session.setLocalSendPort(localPort > 0 ? localPort : srcPort);
            session.setStatus(GbBroadcastSession.STATUS_SENDING);
            session.setSendTime(LocalDateTime.now());
            sessionService.update(session);
            log.info("[{}] startSendRtp 已推音频 设备:{} 通道:{} 目的:{}:{} UDP:{} PT:{}",
                    tag(session), session.getDeviceId(), session.getChannelId(),
                    session.getDstIp(), session.getDstPort(), udp, pt);
        } catch (Exception e) {
            log.error("[{}] startSendRtp 失败 Call-ID:{} 错误:{}", tag(session), callId, e.getMessage(), e);
            cleanup(session, true, true);
        }
    }

    public void onDeviceBye(GbBroadcastSession session) {
        if (session == null) return;
        cleanup(session, false, true);
        log.info("[{}] 设备停播清理完成 设备:{} 通道:{} 流ID:{}",
                tag(session), session.getDeviceId(), session.getChannelId(), session.getSrcStream());
    }

    public void onSendRtpStopped(String stream, String ssrc) {
        for (GbBroadcastSession s : sessionService.listBySrcStream(stream)) {
            if (ssrc == null || ssrc.isBlank() || ssrc.equals(s.getSsrc())) {
                cleanup(s, true, true);
                log.info("[{}] on_send_rtp_stopped 清理 流ID:{} SSRC:{}", tag(s), stream, s.getSsrc());
            }
        }
    }

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
                        tag(s), s.getDeviceId(), s.getChannelId());
                cleanup(s, true, true);
            } else if (waitInviteTimeout) {
                log.warn("[{}] 等待设备反向 INVITE 超时，释放会话 设备:{} 通道:{}",
                        tag(s), s.getDeviceId(), s.getChannelId());
                cleanup(s, false, true);
            }
        }
    }



    public void cleanupCascade(GbBroadcastSession session, boolean byeDevice, boolean byeUpstream) {
        cleanup(session, byeDevice, byeUpstream);
    }

    private void cleanup(GbBroadcastSession session, boolean byeDevice, boolean byeUpstream) {
        String logTag = tag(session);
        MediaServer server = mediaServer(session.getServerId());
        // 级联时源流和收流腿是同一条流，只关一次
        boolean sameRecvStream = session.getRecvStreamId() != null
                && session.getRecvStreamId().equals(session.getSrcStream());

        if (server != null) {
            try {
                zlmRestClient.stopSendRtp(server, session.getSrcApp(), session.getSrcStream(), session.getSsrc());
            } catch (Exception e) {
                log.warn("[{}] stopSendRtp 异常(忽略) 流ID:{} 错误:{}",
                        logTag, session.getSrcStream(), e.getMessage(), e);
            }
        }

        sessionService.remove(session.getBroadcastStreamId());

        if (byeDevice) {
            byeDevice(session);
        }

        // BYE 上级，parentToTag 为空说明上级 Dialog 未建立
        if (byeUpstream && session.getParentPlatformId() != null
                && session.getParentCallId() != null && session.getParentToTag() != null) {
            try {
                GbParentPlatform platform = platformMapper.selectById(session.getParentPlatformId());
                if (platform != null) {
                    platformCommander.broadcastByeCmd(platform, session.getParentCallId(),
                            session.getParentFromTag(), session.getParentToTag());
                }
            } catch (Exception e) {
                log.warn("[{}] 给上级发广播 BYE 异常(忽略) 流ID:{} 错误:{}",
                        logTag, session.getBroadcastStreamId(), e.getMessage(), e);
            }
        }

        if (server != null) {
            try {
                zlmRestClient.closeStream(server, session.getSrcApp(), session.getSrcStream(), true);
            } catch (Exception e) {
                log.warn("[{}] closeStream 异常(忽略) 流ID:{} 错误:{}",
                        logTag, session.getSrcStream(), e.getMessage(), e);
            }
        }

        // 收流腿：关接收端口；与源流不是同一条流时再关流
        if (session.getRecvStreamId() != null) {
            MediaServer recvServer = mediaServer(session.getRecvServerId() != null
                    ? session.getRecvServerId() : session.getServerId());
            if (recvServer != null) {
                try {
                    zlmRestClient.closeRtpServer(recvServer, session.getRecvStreamId());
                } catch (Exception e) {
                    log.warn("[{}] closeRtpServer 异常(忽略) 流ID:{} 错误:{}",
                            logTag, session.getRecvStreamId(), e.getMessage(), e);
                }
                if (!sameRecvStream) {
                    try {
                        zlmRestClient.closeStream(recvServer, RECV_APP, session.getRecvStreamId(), true);
                    } catch (Exception e) {
                        log.warn("[{}] 关闭反向收流异常(忽略) 流ID:{} 错误:{}",
                                logTag, session.getRecvStreamId(), e.getMessage(), e);
                    }
                }
            }
        }

        if (session.getServerId() != null && session.getSsrc() != null) {
            ssrcFactory.release(session.getServerId(), session.getSsrc());
        }
        if (session.getRecvServerId() != null && session.getRecvSsrc() != null) {
            ssrcFactory.release(session.getRecvServerId(), session.getRecvSsrc());
        }
    }

    private void byeDevice(GbBroadcastSession session) {
        if (session.getCallId() == null || session.getFromTag() == null || session.getToTag() == null) {
            return;
        }
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, session.getDeviceId()));
        if (device == null) {
            log.warn("[{}] BYE 设备时设备已删除 设备ID:{}", tag(session), session.getDeviceId());
            return;
        }
        try {
            Request bye = headerProvider.buildBroadcastByeRequest(
                    device, session.getChannelId(), session.getCallId(),
                    session.getFromTag(), session.getToTag());
            sipSender.send(sipLayer.getLocalIp(device.getLocalIp()), bye);
            log.info("[{}] 已向设备发 BYE 设备:{} 通道:{}",
                    tag(session), device.getDeviceId(), session.getChannelId());
        } catch (Exception e) {
            log.warn("[{}] 向设备发 BYE 失败 设备:{} 错误:{}",
                    tag(session), device.getDeviceId(), e.getMessage(), e);
        }
    }

    private void abortSession(GbBroadcastSession session, String reason) {
        sessionService.remove(session.getBroadcastStreamId());
        if (session.getServerId() != null && session.getSsrc() != null) {
            ssrcFactory.release(session.getServerId(), session.getSsrc());
        }
        log.warn("[{}] {}发起失败，会话已释放 设备:{} 通道:{} 原因:{}",
                tag(session), session.isTalk() ? "对讲" : "播报",
                session.getDeviceId(), session.getChannelId(), reason);
    }

    public GbBroadcastSession findWaitingSession(String deviceId, String channelId) {
        GbBroadcastSession session = sessionService.get(buildStreamId(deviceId, channelId));
        if (session == null) {
            session = sessionService.get("cascade_" + deviceId + "_" + channelId);
        }
        if (session == null) {
            return null;
        }
        return GbBroadcastSession.STATUS_WAIT_INVITE.equals(session.getStatus()) ? session : null;
    }

    // 部分厂商反向 INVITE 不带通道码；这台设备只有一个等待会话时才敢兜底，多个就认不出来。
    public GbBroadcastSession findSingleWaitingSession(String deviceId) {
        GbBroadcastSession hit = null;
        for (GbBroadcastSession s : sessionService.listAllActive()) {
            if (!deviceId.equals(s.getDeviceId())
                    || !GbBroadcastSession.STATUS_WAIT_INVITE.equals(s.getStatus())) {
                continue;
            }
            if (hit != null) {
                log.warn("[{}] 设备 {} 有多个等待 INVITE 的会话，通道码缺失无法判定", tag(s), deviceId);
                return null;
            }
            hit = s;
        }
        return hit;
    }

    public GbParentPlatform loadPlatform(String id) {
        return platformMapper.selectById(id);
    }

    public MediaServer loadMediaServer(String serverId) {
        return mediaServer(serverId);
    }

    private boolean waitStreamReady(MediaServer server, String app, String stream) {
        // app=rtp 是对讲设备上行收流，其余为播报/对讲共用的浏览器推流腿
        String logTag = RECV_APP.equals(app) ? TAG_TALK : TAG_BROADCAST;
        if (zlmRestClient.isMediaOnline(server, app, stream)) {
            return true;
        }
        CompletableFuture<Void> f = new CompletableFuture<>();
        readySignals.put(stream, f);
        try {
            f.get(WAIT_STREAM_MS, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            log.warn("[{}] 等源流上线超时 流:{}/{}", logTag, app, stream);
            return zlmRestClient.isMediaOnline(server, app, stream);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return zlmRestClient.isMediaOnline(server, app, stream);
        } catch (ExecutionException e) {
            return zlmRestClient.isMediaOnline(server, app, stream);
        } finally {
            readySignals.remove(stream);
        }
    }

    public void onStreamReady(String app, String stream, String mediaServerId) {
        CompletableFuture<Void> f = readySignals.get(stream);
        if (f != null) {
            f.complete(null);
        }
    }

    public boolean ownsStream(String app, String stream) {
        return stream != null && !sessionService.listBySrcStream(stream).isEmpty();
    }

    private MediaServer mediaServer(String serverId) {
        return mediaServerMapper.selectOne(
                new LambdaQueryWrapper<MediaServer>().eq(MediaServer::getServerId, serverId));
    }

    private Integer sendPortInRange() {
        return sessionService.acquireSendPort(
                properties.getCascade().getSendRtpPortStart(),
                properties.getCascade().getSendRtpPortEnd());
    }

    private static String buildStreamId(String deviceId, String channelId) {
        return "broadcast_" + deviceId + "_" + channelId;
    }

    private static String tag(GbBroadcastSession s) {
        return s != null && s.isTalk() ? TAG_TALK : TAG_BROADCAST;
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
        // 只对浏览器出口签名，级联内部走 minimalResult，别给它塞 wt
        if (session.isTalk() && session.getRecvStreamId() != null && session.getRecvServerId() != null) {
            String rtcPlayUrl = "http://" + ip + ":" + server.getRtcPort()
                    + "/index/api/webrtc?app=" + RECV_APP + "&stream=" + session.getRecvStreamId() + "&type=play";
            ViewActor actor = viewTokenSigner.requireCurrentUserActor();
            resp.setRtcPlayUrl(viewTokenSigner.appendToken(rtcPlayUrl, actor,
                    PlayTarget.rtc(session.getRecvServerId(), RECV_APP, session.getRecvStreamId())));
        }
        return resp;
    }

    private void sendError(SIPRequest request, int code, String logTag) {
        try {
            Response resp = headerProvider.buildResponse(code, request);
            sipSender.send(request.getLocalAddress().getHostAddress(), resp);
        } catch (Exception e) {
            log.warn("[{}] 回 {} 失败 错误:{}", logTag, code, e.getMessage(), e);
        }
    }
}
