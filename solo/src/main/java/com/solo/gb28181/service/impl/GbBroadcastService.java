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

/**
 * 语音播报/语音对讲：平台发 Broadcast 通知 → 设备反向 INVITE 收音频 → ACK 后 ZLM 推流。
 * 源流：本地喊话为浏览器 WebRTC 推流，级联喊话为上级平台推下来的音频。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbBroadcastService {

    /** 单向播报日志标题 */
    private static final String TAG_BROADCAST = "语音播报";
    /** 双向对讲日志标题 */
    private static final String TAG_TALK = "语音对讲";
    private static final String APP = "broadcast";
    // 对讲收设备音频的收流 app
    private static final String RECV_APP = "rtp";

    /** 等设备回 Broadcast 通知 200 的超时 */
    private static final long NOTIFY_TIMEOUT_MS = 12_000L;
    /** 等源流在 ZLM 上线的超时 */
    private static final long WAIT_STREAM_MS = 10_000L;
    /** 等待设备反向 INVITE 回填对讲收流信息的最长时间 */
    private static final long WAIT_RECV_MS = 5_000L;

    /** 源流上线信号，key=srcStream，由 onStreamReady 唤醒 */
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



    /**
     * 网页喊话入口：建会话、发 Broadcast 通知，返回 WebRTC 推流地址。
     */
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

    /**
     * 级联喊话的设备腿。会话已由上级流程建好，这里只校验设备通道并发通知。失败抛异常，由上级流程清场。
     */
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

    /**
     * 播报/对讲/级联共用的发起逻辑。srcApp/srcStream 决定音频源；requireBrowser=false 时不返回 WebRTC 推流地址。
     */
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

        // 同一路播报/对讲只允许一个活动会话
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

    /**
     * 发 Broadcast 通知并同步等 200。失败抛 ServiceException，会话清理由调用方负责。
     */
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

    /**
     * 停止播报/对讲：Dialog 已建立则 BYE 设备，再停推流、关流、释放资源。
     */
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

    /**
     * 代理浏览器与 ZLM 的 WebRTC SDP 交换，浏览器不接触节点 secret。
     */
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

    /**
     * 对讲时前端拉设备音频的 WebRTC SDP 交换（app=rtp，type=play），仅对讲可用。
     */
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
        return zlmRestClient.exchangeWebRtcSdp(server, RECV_APP, session.getRecvStreamId(), "play", req.getSdp());
    }

    /** 该流是否为某对讲会话的设备回传流（rtp/recv_*，ZLM hook 路由用） */
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



    /**
     * 设备收到通知后反向发来的 INVITE（m=audio），由本平台回 200 OK。
     */
    public void onDeviceInvite(SIPRequest request, GbDevice device, String channelId, String deviceSdp) {
        String callId = ((javax.sip.header.CallIdHeader)
                request.getHeader(javax.sip.header.CallIdHeader.NAME)).getCallId();
        String deviceFromTag = request.getFromHeader().getTag();

        // c= 为设备收流 IP，m=audio 端口为设备收流端口
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

        // 回填 Dialog 信息与收流目标，save 会建 callId 反查索引
        String localToTag = SipUtils.newToTag();
        session.setCallId(callId);
        session.setFromTag(deviceFromTag);
        session.setToTag(localToTag);
        session.setDstIp(dstIp);
        session.setDstPort(dstPort);
        session.setTransport(transport);

        // 载荷按设备 offer 选：带 8 走裸 PCMA，只带 96 走 PS
        int audioPt = SdpBuilder.selectAudioPayload(deviceSdp);
        session.setAudioPt(audioPt);
        // SSRC 优先用设备 SDP 的 y=，没有再用本平台分配的
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
            // TCP-active 设备主动来连：先开被动监听，监听端口写进 SDP
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

    /**
     * 设备 ACK。UDP/TCP-active 已在回 200 前建好发送器，幂等跳过；TCP-passive 在此主动连接并推流。
     */
    public void onDeviceAck(String callId) {
        GbBroadcastSession session = sessionService.getByCallId(callId);
        if (session == null) {
            return;
        }
        // callId 索引同时挂了上级腿(parentCallId)，上级 ACK 不在此处理
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
            // 设备 ACK 可能早于源流上线，先等流再推
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

    /**
     * 设备主动 BYE，200 已由 Processor 回过。
     */
    public void onDeviceBye(GbBroadcastSession session) {
        if (session == null) return;
        cleanup(session, false, true);
        log.info("[{}] 设备停播清理完成 设备:{} 通道:{} 流ID:{}",
                tag(session), session.getDeviceId(), session.getChannelId(), session.getSrcStream());
    }

    /**
     * ZLM on_send_rtp_stopped 回调：推流被中止，清理对应会话。
     */
    public void onSendRtpStopped(String stream, String ssrc) {
        for (GbBroadcastSession s : sessionService.listBySrcStream(stream)) {
            if (ssrc == null || ssrc.isBlank() || ssrc.equals(s.getSsrc())) {
                cleanup(s, true, true);
                log.info("[{}] on_send_rtp_stopped 清理 流ID:{} SSRC:{}", tag(s), stream, s.getSsrc());
            }
        }
    }

    /**
     * 定时扫描：通知后不发 INVITE、回 200 后不回 ACK 的会话按超时兜底释放。
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
                        tag(s), s.getDeviceId(), s.getChannelId());
                cleanup(s, true, true);
            } else if (waitInviteTimeout) {
                log.warn("[{}] 等待设备反向 INVITE 超时，释放会话 设备:{} 通道:{}",
                        tag(s), s.getDeviceId(), s.getChannelId());
                // 上级腿可能已建立，cleanup 内部按 parentToTag 决定是否 BYE 上级
                cleanup(s, false, true);
            }
        }
    }



    /** 级联喊话清场入口（上级流程调用） */
    public void cleanupCascade(GbBroadcastSession session, boolean byeDevice, boolean byeUpstream) {
        cleanup(session, byeDevice, byeUpstream);
    }

    /**
     * 统一清理：停推流、删会话、按需 BYE 设备/上级、关流和收流端口、释放 SSRC。
     *
     * @param byeDevice   是否 BYE 设备（设备主动 BYE 或 Dialog 未建立时 false）
     * @param byeUpstream 是否 BYE 上级（上级主动 BYE 或 Dialog 未建立时 false）
     */
    private void cleanup(GbBroadcastSession session, boolean byeDevice, boolean byeUpstream) {
        String logTag = tag(session);
        MediaServer server = mediaServer(session.getServerId());
        // 级联时源流和收流腿是同一条流，只关一次
        boolean sameRecvStream = session.getRecvStreamId() != null
                && session.getRecvStreamId().equals(session.getSrcStream());

        // 停给设备的推流
        if (server != null) {
            try {
                zlmRestClient.stopSendRtp(server, session.getSrcApp(), session.getSrcStream(), session.getSsrc());
            } catch (Exception e) {
                log.warn("[{}] stopSendRtp 异常(忽略) 流ID:{} 错误:{}",
                        logTag, session.getSrcStream(), e.getMessage(), e);
            }
        }

        // 删会话及反查索引
        sessionService.remove(session.getBroadcastStreamId());

        // BYE 设备
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

        // 关源流
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

        // 释放 SSRC
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

    /** start 失败时释放会话和 SSRC，此时无推流无 Dialog */
    private void abortSession(GbBroadcastSession session, String reason) {
        sessionService.remove(session.getBroadcastStreamId());
        if (session.getServerId() != null && session.getSsrc() != null) {
            ssrcFactory.release(session.getServerId(), session.getSsrc());
        }
        log.warn("[{}] {}发起失败，会话已释放 设备:{} 通道:{} 原因:{}",
                tag(session), session.isTalk() ? "对讲" : "播报",
                session.getDeviceId(), session.getChannelId(), reason);
    }

    /**
     * 按 设备+通道 找等待 INVITE 的会话，本地和级联两种流名都查。
     */
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

    /**
     * 设备反向 INVITE 既不带 Subject、Request-URI 也没放通道码（部分厂商只写平台编码）时，
     * 按设备找唯一一个等待 INVITE 的会话兜底；多个在等则无法判定，返回 null。
     */
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

    /** 查上级平台配置（级联清场用） */
    public GbParentPlatform loadPlatform(String id) {
        return platformMapper.selectById(id);
    }

    /** 查流媒体节点 */
    public MediaServer loadMediaServer(String serverId) {
        return mediaServer(serverId);
    }

    /**
     * 等源流在 ZLM 上线。先查在线状态，不在则等 onStreamReady 事件信号，超时返回当前状态。
     */
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

    /**
     * ZLM on_stream_changed(regist=true) 回调：源流上线，唤醒等待中的推流。
     */
    public void onStreamReady(String app, String stream, String mediaServerId) {
        CompletableFuture<Void> f = readySignals.get(stream);
        if (f != null) {
            f.complete(null);
        }
    }

    /** 该流是否为某个会话的源流（ZLM hook 路由用） */
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

    /** 流名：broadcast_<deviceId>_<channelId> */
    private static String buildStreamId(String deviceId, String channelId) {
        return "broadcast_" + deviceId + "_" + channelId;
    }

    /** 按会话模式取日志标题：对讲=[语音对讲]，其余（含 null 兜底）=[语音播报] */
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
        // 对讲多一路拉流地址
        if (session.isTalk() && session.getRecvStreamId() != null) {
            resp.setRtcPlayUrl("http://" + ip + ":" + server.getRtcPort()
                    + "/index/api/webrtc?app=" + RECV_APP + "&stream=" + session.getRecvStreamId() + "&type=play");
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
