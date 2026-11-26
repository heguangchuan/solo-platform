package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.CascadeSendSession;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.mapper.GbParentPlatformMapper;
import com.solo.gb28181.request.PlayStartRequest;
import com.solo.gb28181.response.PlayResultResponse;
import com.solo.gb28181.service.CascadeSendSessionService;
import com.solo.gb28181.service.CatalogShareService;
import com.solo.gb28181.service.GbInviteSessionCleanupService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.service.GbParentPlatformService;
import com.solo.gb28181.service.GbPlayService;
import com.solo.gb28181.sip.SipSender;
import com.solo.gb28181.transmit.cmd.PlatformSipHeaderProvider;
import com.solo.gb28181.transmit.cmd.SdpBuilder;
import com.solo.gb28181.utils.SipUtils;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import tools.jackson.databind.JsonNode;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sip.message.Request;
import javax.sip.message.Response;
import java.time.LocalDateTime;
import java.util.List;

// 上级 INVITE 后先从真实设备取流，再用 startSendRtp 推给上级。
// SSRC 直接用上级 SDP 的 y=；UDP/TCP-passive 等 ACK，TCP-active 回 200 OK 时先开监听。
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
    private final GbMediaServerMapper mediaServerMapper;
    private final PlatformSipHeaderProvider headerProvider;
    private final SipSender sipSender;
    private final GbParentPlatformMapper gbParentPlatformMapper;

    public void onUpstreamInvite(SIPRequest request, GbParentPlatform platform,
                                 String channelId, String upstreamSdp) {
        javax.sip.header.CallIdHeader callIdHeader =
                (javax.sip.header.CallIdHeader) request.getHeader(javax.sip.header.CallIdHeader.NAME);
        String callId = callIdHeader.getCallId();
        String upstreamFromTag = request.getFromHeader().getTag();

        GbDeviceChannel channel = findSharedChannel(platform.getId(), channelId);
        if (channel == null) {
            log.warn("[{}] 上级 {} 点播未共享/不存在的通道 上级通道编码:{} 原因:回 404",
                    LOG_TAG, platform.getServerGbId(), channelId);
            sendError(request, Response.NOT_FOUND, "channel not shared");
            return;
        }

        // 上级给什么收流地址就用什么，网络不通让上级改 SDP，别在这里偷偷换
        String dstIp = SdpBuilder.extractConnectionIp(upstreamSdp);
        int dstPort = SdpBuilder.extractMediaPort(upstreamSdp);
        String ssrc = SdpBuilder.extractSsrc(upstreamSdp);
        String transport = SdpBuilder.extractTransport(upstreamSdp);
        if (dstIp == null || dstPort <= 0 || ssrc == null || ssrc.isBlank()) {
            log.warn("[{}] 上级 SDP 缺 c=/m=/y= 关键字段 目的IP:{} 目的端口:{} SSRC:{} 原因:回 400",
                    LOG_TAG, dstIp, dstPort, ssrc);
            sendError(request, Response.BAD_REQUEST, "invalid sdp");
            return;
        }
        log.info("[{}] 收到上级点播 上级平台:{} 上级通道编码:{} 本平台通道编码:{} 收流目标:{}:{} SSRC:{} 传输方式:{}",
                LOG_TAG, platform.getServerGbId(), channelId, channel.getChannelId(), dstIp, dstPort, ssrc, transport);

        // 上级传来的可能是 exposed_code，设备只认真实 deviceId/channelId
        PlayResultResponse play;
        try {
            PlayStartRequest req = new PlayStartRequest();
            req.setDeviceId(channel.getDeviceId());
            req.setChannelId(channel.getChannelId());
            req.setTransport(null);        // 取流侧传输由默认策略决定，与上级收流传输无关
            req.setStreamNumber(null);     // 级联点播固定主码流
            play = playService.start(req);
        } catch (Exception e) {
            log.warn("[{}] 取流失败 上级平台:{} 上级通道编码:{} 本平台通道编码:{} 错误:{}",
                    LOG_TAG, platform.getServerGbId(), channelId, channel.getChannelId(), e.getMessage(), e);
            sendError(request, Response.BUSY_HERE, "fetch stream failed");
            return;
        }

        MediaServer server = mediaServer(play.getMediaServerId());
        if (server == null) {
            log.error("[{}] 取流就绪但节点丢失 节点ID:{}", LOG_TAG, play.getMediaServerId());
            sendError(request, Response.SERVER_INTERNAL_ERROR, "media server lost");
            return;
        }

        String localToTag = SipUtils.newToTag();
        CascadeSendSession session = new CascadeSendSession();
        session.setSendStreamId("send_" + callId);
        session.setServerId(server.getServerId());
        session.setPlatformDbId(platform.getId());
        session.setPlatformServerGbId(platform.getServerGbId());
        session.setLocalGbId(properties.getId());
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

        // 先存会话再开监听，不然 on_play 可能先到，内部 reader 会被误拒
        sendSessionService.save(session);

        int sendPort = 0;
        boolean upstreamActive = session.isUpstreamActive();
        if (upstreamActive) {
            try {
                sendPort = zlmRestClient.startSendRtpPassive(server, APP, play.getStreamId(), ssrc);
                session.setLocalSendPort(sendPort);
                session.setStatus(CascadeSendSession.STATUS_SENDING);
                session.setSendTime(LocalDateTime.now());
                sendSessionService.update(session);
            } catch (Exception e) {
                sendSessionService.removeBySendStreamId(session.getSendStreamId());
                log.error("[{}] startSendRtpPassive 失败 流ID:{} 错误:{}",
                        LOG_TAG, play.getStreamId(), e.getMessage(), e);
                sendError(request, Response.SERVER_INTERNAL_ERROR, "passive listen failed");
                return;
            }
        }

        String sdpIp = server.getStreamIp() != null && !server.getStreamIp().isBlank()
                ? server.getStreamIp() : server.getHostIp();
        String answer = SdpBuilder.buildSendAnswer(properties.getId(), sdpIp, sendPort, ssrc, transport);
        try {
            SIPResponse ok = headerProvider.buildInviteOk(platform, request, answer, localToTag);
            sipSender.send(request.getLocalAddress().getHostAddress(), ok);
            log.info("[{}] 回 200 OK 上级平台:{} 本平台通道编码:{} 源流ID:{} 传输方式:{} 待ACK:{}",
                    LOG_TAG, platform.getServerGbId(), channel.getChannelId(), play.getStreamId(),
                    transport, !upstreamActive);
        } catch (Exception e) {
            log.error("[{}] 回 200 OK 失败 Call-ID:{} 错误:{}", LOG_TAG, callId, e.getMessage(), e);
            cleanup(session, true);
        }
    }

    public void onUpstreamAck(String callId) {
        CascadeSendSession session = sendSessionService.getByCallId(callId);
        if (session == null) {
            return;
        }
        if (CascadeSendSession.STATUS_SENDING.equals(session.getStatus())) {
            // TCP 主动已经开过监听；重复 ACK 也直接跳过
            return;
        }
        MediaServer server = mediaServer(session.getServerId());
        if (server == null) {
            log.error("[{}] ACK 时节点丢失 节点ID:{}", LOG_TAG, session.getServerId());
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
            log.info("[{}] startSendRtp 已推流 上级平台:{} 本平台通道编码:{} 目的:{}:{} UDP:{}",
                    LOG_TAG, session.getPlatformServerGbId(), session.getLocalChannelId(),
                    session.getDstIp(), session.getDstPort(), udp);
        } catch (Exception e) {
            log.error("[{}] startSendRtp 失败 Call-ID:{} 错误:{}", LOG_TAG, callId, e.getMessage(), e);
            cleanup(session, true);
        }
    }

    public void onUpstreamBye(CascadeSendSession session) {
        if (session == null) return;
        cleanup(session, false);
        log.info("[{}] 上级停播清理完成 上级平台:{} 本平台通道编码:{} 源流ID:{}",
                LOG_TAG, session.getPlatformServerGbId(), session.getLocalChannelId(), session.getSrcStream());
    }

    public void onSendRtpStopped(String stream, String ssrc) {
        List<CascadeSendSession> sessions = sendSessionService.listBySrcStream(stream);
        for (CascadeSendSession s : sessions) {
            if (ssrc == null || ssrc.isBlank() || ssrc.equals(s.getSsrc())) {
                cleanup(s, true);
                log.info("[{}] on_send_rtp_stopped 清理 流ID:{} SSRC:{}", LOG_TAG, stream, s.getSsrc());
            }
        }
    }

    public void sweepAckTimeout() {
        long timeoutMs = properties.getCascade().getSendAckTimeoutMs();
        LocalDateTime deadline = LocalDateTime.now().minusNanos(timeoutMs * 1_000_000L);
        for (CascadeSendSession s : sendSessionService.listAllActive()) {
            if (CascadeSendSession.STATUS_WAIT_ACK.equals(s.getStatus())
                    && s.getCreateTime() != null && s.getCreateTime().isBefore(deadline)) {
                log.warn("[{}] 等待上级 ACK 超时，释放并 BYE 上级 上级平台:{} 本平台通道编码:{}",
                        LOG_TAG, s.getPlatformServerGbId(), s.getLocalChannelId());
                cleanup(s, true);
            }
        }
    }


    private void cleanup(CascadeSendSession session, boolean byeUpstream) {
        MediaServer server = mediaServer(session.getServerId());

        if (server != null) {
            try {
                zlmRestClient.stopSendRtp(server, session.getSrcApp(), session.getSrcStream(), session.getSsrc());
            } catch (Exception e) {
                log.warn("[{}] stopSendRtp 异常(忽略) 源流ID:{} 错误:{}", LOG_TAG, session.getSrcStream(), e.getMessage(), e);
            }
        }

        sendSessionService.removeBySendStreamId(session.getSendStreamId());

        if (byeUpstream) {
            byeUpstream(session);
        }

        try {
            boolean stillSharing = !sendSessionService.listBySrcStream(session.getSrcStream()).isEmpty();
            if (!stillSharing && server != null) {
                GbInviteSession invite = inviteSessionService.getByStreamId(session.getSrcStream());
                if (invite != null && !streamHasReader(server, session.getSrcStream())) {
                    support.failAndBye(invite, LOG_TAG, "级联无观看者回收取流");
                }
            }
        } catch (Exception e) {
            log.warn("[{}] 取流回收判断异常 源流ID:{} 错误:{}", LOG_TAG, session.getSrcStream(), e.getMessage(), e);
        }
    }

    private void byeUpstream(CascadeSendSession session) {
        GbParentPlatform p = gbParentPlatformMapper.selectById(session.getPlatformDbId());
        if (p == null) {
            log.warn("[{}] BYE 上级时平台已删除 平台ID:{}", LOG_TAG, session.getPlatformDbId());
            return;
        }
        try {
            Request request = headerProvider.createByeToUpstream(
                    p, session.getCallId(), session.getFromTag(), session.getToTag());
            sipSender.send(headerProvider.localIp(), request);
            log.info("[{}] 已向上级发 BYE 上级平台={} 本平台通道编码={}", LOG_TAG, p.getServerGbId(), session.getLocalChannelId());
        } catch (Exception e) {
            log.warn("[{}] 向上级发 BYE 失败 上级平台:{} 错误:{}", LOG_TAG, p.getServerGbId(), e.getMessage(), e);
        }
    }

    // 优先 exposed_code；老数据没有这个码才回退原始 channelId。
    // 原始码可能撞，真撞了至少把错误日志打出来，别装没事。
    private GbDeviceChannel findSharedChannel(Long platformDbId, String channelId) {
        List<GbDeviceChannel> shared = catalogShareService.loadSharedChannels(platformDbId);
        if (shared == null) return null;
        GbDeviceChannel byExposed = shared.stream()
                .filter(c -> channelId.equals(c.getExposedCode()))
                .findFirst()
                .orElse(null);
        if (byExposed != null) return byExposed;
        List<GbDeviceChannel> byRaw = shared.stream()
                .filter(c -> channelId.equals(c.getChannelId()))
                .toList();
        if (byRaw.isEmpty()) return null;
        if (byRaw.size() > 1) {
            log.error("[{}] 上级点播 上级通道编码:{} 按原始码命中 {} 条(撞码且未分配 exposed_code)，取第一条，可能推错流",
                    LOG_TAG, channelId, byRaw.size());
        }
        return byRaw.getFirst();
    }

    private MediaServer mediaServer(String serverId) {
        return mediaServerMapper.selectOne(
                new LambdaQueryWrapper<MediaServer>().eq(MediaServer::getServerId, serverId));
    }

    private Integer sendPortInRange() {
        return sendSessionService.acquireSendPort(
                properties.getCascade().getSendRtpPortStart(),
                properties.getCascade().getSendRtpPortEnd());
    }

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
            log.warn("[{}] 回 {} 失败 错误:{}", LOG_TAG, code, e.getMessage(), e);
        }
    }
}
