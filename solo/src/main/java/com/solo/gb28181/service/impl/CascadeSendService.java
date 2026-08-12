package com.solo.gb28181.service.impl;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.CascadeSendSession;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.request.PlayStartRequest;
import com.solo.gb28181.response.PlayResultResponse;
import com.solo.gb28181.service.CascadeSendSessionService;
import com.solo.gb28181.service.CatalogShareService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.service.GbParentPlatformService;
import com.solo.gb28181.service.GbPlayService;
import com.solo.gb28181.service.GbInviteSessionCleanupService;
import com.solo.gb28181.sip.SipSender;
import com.solo.gb28181.transmit.cmd.PlatformSipHeaderProvider;
import com.solo.gb28181.transmit.cmd.SdpBuilder;
import com.solo.gb28181.utils.SipUtils;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.service.MediaServerService;
import com.fasterxml.jackson.databind.JsonNode;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sip.message.Request;
import javax.sip.message.Response;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 级联被上级点播编排（本平台作为下级，上级发 INVITE 点播本平台共享通道）。
 *
 * <p>两条腿模型（见 ADR-019）：</p>
 * <ol>
 *   <li><strong>取流腿</strong>：复用 {@link GbPlayService#start} 向真实下级设备取流，流进 ZLM（app=rtp/srcStream）。
 *       这一步阻塞等流就绪，由 {@code InviteRequestProcessor} 丢到 gb28181 业务线程池执行，不阻塞 SIP 线程（ADR-007）。</li>
 *   <li><strong>推流腿</strong>：取流就绪后用 ZLM {@code startSendRtp} 把 srcStream 推到上级 SDP 给的 ip/port/ssrc。</li>
 * </ol>
 *
 * <p>SSRC 策略：直接用上级 SDP 的 {@code y=} 值（顺从上级指定，见 ADR-019 决策 3），不自行分配。</p>
 * <p>推流触发时机：UDP / TCP 被动（本平台主动去连上级）在收到上级 ACK 后 {@code startSendRtp}；
 * TCP 主动（上级来连本平台）在回 200 OK 后立即 {@code startSendRtpPassive} 开监听。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CascadeSendService {

    private static final String LOG_TAG = "级联点播";
    private static final String APP = "rtp";

    private final Gb28181Properties properties;
    private final CatalogShareService catalogShareService;
    private final CascadeSendSessionService sendSessionService;
    private final GbInviteSessionService inviteSessionService;
    private final GbParentPlatformService gbParentPlatformService;
    private final GbPlayService playService;
    private final GbInviteSessionCleanupService support;
    private final ZlmRestClient zlmRestClient;
    private final MediaServerService mediaServerService;
    private final PlatformSipHeaderProvider headerProvider;
    private final SipSender sipSender;

    /**
     * 处理上级 INVITE：校验共享 → 取流 → 建会话 → 回 200 OK（sendonly SDP）。
     * <p>调用方（InviteRequestProcessor）已回过 100 Trying 且在业务线程池内执行。</p>
     */
    public void onUpstreamInvite(SIPRequest request, GbParentPlatform platform,
                                 String channelId, String upstreamSdp) {
        javax.sip.header.CallIdHeader callIdHeader =
                (javax.sip.header.CallIdHeader) request.getHeader(javax.sip.header.CallIdHeader.NAME);
        String callId = callIdHeader.getCallId();
        String upstreamFromTag = request.getFromHeader().getTag();

        // 1) 校验通道确已共享给该上级（loadSharedChannels 走 JOIN 且只筛 parental=0 通道，天然做授权 + 定位真实设备）
        GbDeviceChannel channel = findSharedChannel(platform.getId(), channelId);
        if (channel == null) {
            log.warn("[{}] 上级 {} 点播未共享/不存在的通道 上级通道编码(exposedCode)={}，回 404",
                    LOG_TAG, platform.getServerGbId(), channelId);
            sendError(request, Response.NOT_FOUND, "channel not shared");
            return;
        }

        // 2) 解析上级期望收流的目的地址 / SSRC / 传输模式
        String dstIp = SdpBuilder.extractConnectionIp(upstreamSdp);
        int dstPort = SdpBuilder.extractMediaPort(upstreamSdp);
        String ssrc = SdpBuilder.extractSsrc(upstreamSdp);
        String transport = SdpBuilder.extractTransport(upstreamSdp);
        // 如果上级平台配置了收流IP覆盖（双栈内网互通场景），强制覆盖SDP解析出的地址
        if (platform.getRecvIp() != null && !platform.getRecvIp().isBlank()) {
            log.info("[{}] 上级平台 {} 配置了收流IP覆盖：原地址(SDP) = {}，新地址(覆盖) = {}",
                    LOG_TAG, platform.getServerGbId(), dstIp, platform.getRecvIp());
            dstIp = platform.getRecvIp();
        }
        if (dstIp == null || dstPort <= 0 || ssrc == null || ssrc.isBlank()) {
            log.warn("[{}] 上级 SDP 缺 c=/m=/y= 关键字段 dstIp={} dstPort={} ssrc={}，回 400",
                    LOG_TAG, dstIp, dstPort, ssrc);
            sendError(request, Response.BAD_REQUEST, "invalid sdp");
            return;
        }
        log.info("[{}] 收到上级点播 上级平台={} 上级通道编码(exposedCode)={} 本平台通道编码={} 收流目标={}:{} ssrc={} 传输方式={}",
                LOG_TAG, platform.getServerGbId(), channelId, channel.getChannelId(), dstIp, dstPort, ssrc, transport);

        // 3) 向真实下级设备取流（阻塞等流就绪；复用设备点播全链路）
        //    注意：上级来的 channelId 可能是 exposed_code，取流必须用真实通道原始 channelId + deviceId，
        //    否则下级设备不认识 exposed_code。二者已由 findSharedChannel 解析到 channel 实体。
        PlayResultResponse play;
        try {
            PlayStartRequest req = new PlayStartRequest();
            req.setDeviceId(channel.getDeviceId());
            req.setChannelId(channel.getChannelId());
            req.setTransport(null);        // 取流侧传输由默认策略决定，与上级收流传输无关
            req.setStreamNumber(null);     // 级联点播固定主码流
            play = playService.start(req);
        } catch (Exception e) {
            log.warn("[{}] 取流失败 上级平台={} 上级通道编码(exposedCode)={} 本平台通道编码={}: {}",
                    LOG_TAG, platform.getServerGbId(), channelId, channel.getChannelId(), e.getMessage());
            sendError(request, Response.BUSY_HERE, "fetch stream failed");
            return;
        }

        MediaServer server = mediaServer(play.getMediaServerId());
        if (server == null) {
            log.error("[{}] 取流就绪但节点丢失 serverId={}", LOG_TAG, play.getMediaServerId());
            sendError(request, Response.SERVER_INTERNAL_ERROR, "media server lost");
            return;
        }

        // 4) 建推流会话
        String localToTag = SipUtils.newToTag();
        CascadeSendSession session = new CascadeSendSession();
        session.setSendStreamId("send_" + callId);
        session.setServerId(server.getServerId());
        session.setPlatformDbId(platform.getId());
        session.setPlatformServerGbId(platform.getServerGbId());
        session.setLocalGbId(platform.getDeviceGbId());
        session.setChannelId(channelId);
        session.setLocalChannelId(channel.getChannelId());
        session.setDeviceId(channel.getDeviceId());
        session.setSrcApp(APP);
        session.setSrcStream(play.getStreamId());
        session.setDstIp(dstIp);
        session.setDstPort(dstPort);
        session.setSsrc(ssrc);
        session.setTransport(transport);
        session.setCallId(callId);
        session.setFromTag(upstreamFromTag);
        session.setToTag(localToTag);
        session.setStatus(CascadeSendSession.STATUS_WAIT_ACK);
        session.setCreateTime(LocalDateTime.now());

        // 5) TCP 主动（上级来连本平台）：先开监听端口拿到本地端口，写进回复 SDP
        int sendPort = 0;
        boolean upstreamActive = session.isUpstreamActive();
        if (upstreamActive) {
            try {
                sendPort = zlmRestClient.startSendRtpPassive(server, APP, play.getStreamId(), ssrc);
                session.setLocalSendPort(sendPort);
                session.setStatus(CascadeSendSession.STATUS_SENDING);
                session.setSendTime(LocalDateTime.now());
            } catch (Exception e) {
                log.error("[{}] startSendRtpPassive 失败 stream={}: {}",
                        LOG_TAG, play.getStreamId(), e.getMessage());
                sendError(request, Response.SERVER_INTERNAL_ERROR, "passive listen failed");
                return;
            }
        }

        sendSessionService.save(session);

        // 6) 回 200 OK（sendonly SDP）
        String sdpIp = server.getStreamIp() != null && !server.getStreamIp().isBlank()
                ? server.getStreamIp() : server.getHostIp();
        String answer = SdpBuilder.buildSendAnswer(platform.getDeviceGbId(), sdpIp, sendPort, ssrc, transport);
        try {
            SIPResponse ok = headerProvider.buildInviteOk(platform, request, answer, localToTag);
            sipSender.send(request.getLocalAddress().getHostAddress(), ok);
            log.info("[{}] 回 200 OK 上级平台={} 本平台通道编码={} srcStream={} 传输方式={} 待ACK={}",
                    LOG_TAG, platform.getServerGbId(), channel.getChannelId(), play.getStreamId(),
                    transport, !upstreamActive);
        } catch (Exception e) {
            log.error("[{}] 回 200 OK 失败 callId={}: {}", LOG_TAG, callId, e.getMessage());
            cleanup(session, true);
        }
    }

    /**
     * 上级 ACK 到达：对 UDP / TCP 被动会话真正 startSendRtp（TCP 主动已在回 200 时开监听，跳过）。
     */
    public void onUpstreamAck(String callId) {
        CascadeSendSession session = sendSessionService.getByCallId(callId);
        if (session == null) {
            return;
        }
        if (CascadeSendSession.STATUS_SENDING.equals(session.getStatus())) {
            // TCP 主动已在回 200 时启动监听，或重复 ACK，幂等跳过
            return;
        }
        MediaServer server = mediaServer(session.getServerId());
        if (server == null) {
            log.error("[{}] ACK 时节点丢失 serverId={}", LOG_TAG, session.getServerId());
            cleanup(session, true);
            return;
        }
        try {
            // 站在上级视角：TCP-passive 表示上级等本平台去连 → 本平台主动去连（is_udp=0）；UDP 直推
            boolean udp = !session.isTcp();
            Integer srcPort = sendPortInRange();
            int localPort = zlmRestClient.startSendRtp(server, session.getSrcApp(), session.getSrcStream(),
                    session.getSsrc(), session.getDstIp(), session.getDstPort(), udp, srcPort);
            session.setLocalSendPort(localPort > 0 ? localPort : srcPort);
            session.setStatus(CascadeSendSession.STATUS_SENDING);
            session.setSendTime(LocalDateTime.now());
            sendSessionService.update(session);
            log.info("[{}] startSendRtp 已推流 上级平台={} 本平台通道编码={} dst={}:{} udp={}",
                    LOG_TAG, session.getPlatformServerGbId(), session.getLocalChannelId(),
                    session.getDstIp(), session.getDstPort(), udp);
        } catch (Exception e) {
            log.error("[{}] startSendRtp 失败 callId={}: {}", LOG_TAG, callId, e.getMessage());
            cleanup(session, true);
        }
    }

    /**
     * 上级发 BYE 停播（由 ByeRequestProcessor 在按 callId 命中推流会话时调用）。
     */
    public void onUpstreamBye(CascadeSendSession session) {
        if (session == null) return;
        cleanup(session, false);
        log.info("[{}] 上级停播清理完成 上级平台={} 本平台通道编码={} srcStream={}",
                LOG_TAG, session.getPlatformServerGbId(), session.getLocalChannelId(), session.getSrcStream());
    }

    /**
     * ZLM {@code on_send_rtp_stopped} 回调：某路推流被 ZLM 中止（对端断开 / 超时），清理会话。
     */
    public void onSendRtpStopped(String stream, String ssrc) {
        List<CascadeSendSession> sessions = sendSessionService.listBySrcStream(stream);
        for (CascadeSendSession s : sessions) {
            if (ssrc == null || ssrc.isBlank() || ssrc.equals(s.getSsrc())) {
                cleanup(s, true);
                log.info("[{}] on_send_rtp_stopped 清理 stream={} ssrc={}", LOG_TAG, stream, s.getSsrc());
            }
        }
    }

    /**
     * 扫描 WAIT_ACK 超时会话：回 200 OK 后迟迟收不到上级 ACK → 释放并 BYE 上级。由 GbScheduledTasks 定时调用。
     */
    public void sweepAckTimeout() {
        long timeoutMs = properties.getCascade().getSendAckTimeoutMs();
        LocalDateTime deadline = LocalDateTime.now().minusNanos(timeoutMs * 1_000_000L);
        for (CascadeSendSession s : sendSessionService.listAllActive()) {
            if (CascadeSendSession.STATUS_WAIT_ACK.equals(s.getStatus())
                    && s.getCreateTime() != null && s.getCreateTime().isBefore(deadline)) {
                log.warn("[{}] 等待上级 ACK 超时，释放并 BYE 上级 上级平台={} 本平台通道编码={}",
                        LOG_TAG, s.getPlatformServerGbId(), s.getLocalChannelId());
                cleanup(s, true);
            }
        }
    }

    // ---------------- helpers ----------------

    /**
     * 统一清理：stopSendRtp → 删会话 → BYE 上级(可选) → 无人观看时停取流。
     *
     * @param byeUpstream 是否需要本平台主动向上级发 BYE（上级主动 BYE 的场景传 false，其余传 true）
     */
    private void cleanup(CascadeSendSession session, boolean byeUpstream) {
        MediaServer server = mediaServer(session.getServerId());

        // 1) 停推流
        if (server != null) {
            try {
                zlmRestClient.stopSendRtp(server, session.getSrcApp(), session.getSrcStream(), session.getSsrc());
            } catch (Exception e) {
                log.warn("[{}] stopSendRtp 异常（忽略） stream={}: {}", LOG_TAG, session.getSrcStream(), e.getMessage());
            }
        }

        // 2) 删推流会话
        sendSessionService.removeBySendStreamId(session.getSendStreamId());

        // 3) 主动 BYE 上级（超时 / startSendRtp 失败等本平台主动结束的场景）
        if (byeUpstream) {
            byeUpstream(session);
        }

        // 4) 若这路源流已无其它上级在推、且本地也无人观看，则向真实下级停取流回收
        try {
            boolean stillSharing = !sendSessionService.listBySrcStream(session.getSrcStream()).isEmpty();
            if (!stillSharing && server != null) {
                GbInviteSession invite = inviteSessionService.getByStreamId(session.getSrcStream());
                if (invite != null && !streamHasReader(server, session.getSrcStream())) {
                    // 复用设备点播语义：BYE 下级 + closeRtpServer + 释放 SSRC + 删会话
                    support.failAndBye(invite, LOG_TAG, "级联无观看者回收取流");
                }
            }
        } catch (Exception e) {
            log.warn("[{}] 取流回收判断异常 srcStream={}: {}", LOG_TAG, session.getSrcStream(), e.getMessage());
        }
    }

    private void byeUpstream(CascadeSendSession session) {
        GbParentPlatform p = gbParentPlatformService.getById(session.getPlatformDbId());
        if (p == null) {
            log.warn("[{}] BYE 上级时平台已删除 platformDbId={}", LOG_TAG, session.getPlatformDbId());
            return;
        }
        try {
            Request request = headerProvider.createByeToUpstream(
                    p, session.getCallId(), session.getFromTag(), session.getToTag());
            sipSender.send(headerProvider.localIp(p), request);
            log.info("[{}] 已向上级发 BYE 上级平台={} 本平台通道编码={}", LOG_TAG, p.getServerGbId(), session.getLocalChannelId());
        } catch (Exception e) {
            log.warn("[{}] 向上级发 BYE 失败 platform={}: {}", LOG_TAG, p.getServerGbId(), e.getMessage());
        }
    }

    /**
     * 从 loadSharedChannels（仅 parental=0 通道）结果里定位上级点播的真实通道（含共享授权校验）。
     * <p>上级 INVITE 带的 channelId 是本平台对它暴露的码：优先按 exposed_code 精确匹配（平台唯一，
     * 跨设备/级联撞码也能唯一定位）；未命中再回退按原始 channelId 匹配（兼容未分配 exposed_code 的存量，
     * 但存量撞码时只能取第一条并告警）。</p>
     */
    private GbDeviceChannel findSharedChannel(Long platformDbId, String channelId) {
        List<GbDeviceChannel> shared = catalogShareService.loadSharedChannels(platformDbId);
        if (shared == null) return null;
        // 1) 优先 exposed_code 精确匹配
        GbDeviceChannel byExposed = shared.stream()
                .filter(c -> channelId.equals(c.getExposedCode()))
                .findFirst()
                .orElse(null);
        if (byExposed != null) return byExposed;
        // 2) 回退原始 channelId（存量兼容）
        List<GbDeviceChannel> byRaw = shared.stream()
                .filter(c -> channelId.equals(c.getChannelId()))
                .toList();
        if (byRaw.isEmpty()) return null;
        if (byRaw.size() > 1) {
            log.warn("[{}] 上级点播 上级通道编码(exposedCode)={} 按原始码命中 {} 条（撞码且未分配 exposed_code），取第一条，可能推错流；"
                            + "建议重新保存该平台通道推送以分配 exposed_code",
                    LOG_TAG, channelId, byRaw.size());
        }
        return byRaw.get(0);
    }

    private MediaServer mediaServer(String serverId) {
        return mediaServerService.lambdaQuery().eq(MediaServer::getServerId, serverId).one();
    }

    private Integer sendPortInRange() {
        return sendSessionService.acquireSendPort(
                properties.getCascade().getSendRtpPortStart(),
                properties.getCascade().getSendRtpPortEnd());
    }

    /** getMediaList 里 totalReaderCount / readerCount > 0 视为仍有观看者 */
    private boolean streamHasReader(MediaServer server, String stream) {
        for (JsonNode node : zlmRestClient.getMediaList(server)) {
            if (APP.equals(node.path("app").asText()) && stream.equals(node.path("stream").asText())) {
                return node.path("totalReaderCount").asInt(0) > 0 || node.path("readerCount").asInt(0) > 0;
            }
        }
        return false;
    }

    private void sendError(SIPRequest request, int code, String reason) {
        try {
            SIPResponse resp = headerProvider.buildResponse(request, code, reason);
            sipSender.send(request.getLocalAddress().getHostAddress(), resp);
        } catch (Exception e) {
            log.warn("[{}] 回 {} 失败: {}", LOG_TAG, code, e.getMessage());
        }
    }
}
