package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbBroadcastSession;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.entity.GbPlatformShare;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbPlatformShareMapper;
import com.solo.gb28181.service.GbBroadcastSessionService;
import com.solo.gb28181.sip.SipSender;
import com.solo.gb28181.transmit.cmd.PlatformCommander;
import com.solo.gb28181.transmit.cmd.PlatformSipHeaderProvider;
import com.solo.gb28181.transmit.cmd.SdpBuilder;
import com.solo.gb28181.utils.SipUtils;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.service.MediaServerSelector;
import com.solo.media.ssrc.SsrcFactory;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sip.header.CallIdHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 级联喊话：上级发 Broadcast NOTIFY → 本平台回 OK 后反向 INVITE 上级收音频 → 再通知下级设备并转推。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbPlatformBroadcastService {

    private static final String LOG_TAG = "级联喊话";
    private static final String SRC_APP = "rtp";

    private final Gb28181Properties properties;
    private final GbBroadcastService gbBroadcastService;
    private final GbDeviceChannelMapper channelMapper;
    private final GbPlatformShareMapper shareMapper;
    private final GbBroadcastSessionService sessionService;
    private final ZlmRestClient zlmRestClient;
    private final MediaServerSelector mediaServerSelector;
    private final SsrcFactory ssrcFactory;
    private final PlatformSipHeaderProvider headerProvider;
    private final PlatformCommander platformCommander;
    private final SipSender sipSender;

    /** 慢活丢业务线程池，不阻塞 SIP 线程 */
    private final ThreadPoolExecutor gb28181BizExecutor;

    /**
     * 上级喊话通知入口。校验不过回 ERROR；回 OK 后异步反向 INVITE 上级。
     */
    public void onBroadcastNotify(GbParentPlatform platform, String channelId, int sn) {
        GbDeviceChannel channel = resolveChannel(platform, channelId);
        if (channel == null) {
            log.warn("[{}] 通道不存在或未共享，回 ERROR 上级:{} 通道:{}",
                    LOG_TAG, platform.getServerGbId(), channelId);
            platformCommander.broadcastResultCmd(platform, sn, channelId, false);
            return;
        }
        // 同一通道已在喊，拒绝重复建立
        String cascadeStreamId = "cascade_" + channel.getDeviceId() + "_" + channel.getChannelId();
        GbBroadcastSession active = sessionService.get(cascadeStreamId);
        if (active != null && !GbBroadcastSession.STATUS_STOPPED.equals(active.getStatus())) {
            log.warn("[{}] 通道正在喊话中，回 ERROR 上级:{} 流:{} 状态:{}",
                    LOG_TAG, platform.getServerGbId(), cascadeStreamId, active.getStatus());
            platformCommander.broadcastResultCmd(platform, sn, channelId, false);
            return;
        }
        platformCommander.broadcastResultCmd(platform, sn, channelId, true);
        log.info("[{}] 收到上级喊话通知已回 OK 上级:{} 通道:{}", LOG_TAG, platform.getServerGbId(), channelId);

        gb28181BizExecutor.execute(() -> inviteUpstream(platform, channel, channelId));
    }

    /**
     * 开收流端口、建会话、反向 INVITE 上级。upstreamChannelId 为上级通知里的通道码，写 INVITE Subject。
     */
    private void inviteUpstream(GbParentPlatform platform, GbDeviceChannel channel, String upstreamChannelId) {
        MediaServer server = mediaServerSelector.pick();
        if (server == null) {
            log.error("[{}] 无可用流媒体节点 上级:{} 通道:{}",
                    LOG_TAG, platform.getServerGbId(), channel.getChannelId());
            return;
        }

        String broadcastStreamId = "cascade_" + channel.getDeviceId() + "_" + channel.getChannelId();
        String recvStreamId = "recv_" + broadcastStreamId;

        String deviceSsrc = ssrcFactory.acquire(server.getServerId(), false);
        String recvSsrc = ssrcFactory.acquire(server.getServerId(), false);
        int recvPort;
        try {
            recvPort = zlmRestClient.openRtpServer(server, recvStreamId, 0, 0);
        } catch (Exception e) {
            log.error("[{}] 开收流端口失败 上级:{} 流:{} 错误:{}",
                    LOG_TAG, platform.getServerGbId(), recvStreamId, e.getMessage(), e);
            ssrcFactory.release(server.getServerId(), deviceSsrc);
            ssrcFactory.release(server.getServerId(), recvSsrc);
            return;
        }

        CallIdHeader callId = headerProvider.newCallId(platform);
        if (callId == null || callId.getCallId() == null) {
            log.error("[{}] 生成本地 Call-Id 失败，放弃 上级:{}",
                    LOG_TAG, platform.getServerGbId());
            zlmRestClient.closeRtpServer(server, recvStreamId);
            ssrcFactory.release(server.getServerId(), deviceSsrc);
            ssrcFactory.release(server.getServerId(), recvSsrc);
            return;
        }
        String fromTag = SipUtils.newFromTag();

        GbBroadcastSession session = new GbBroadcastSession();
        session.setBroadcastStreamId(broadcastStreamId);
        session.setServerId(server.getServerId());
        session.setDeviceId(channel.getDeviceId());
        session.setChannelId(channel.getChannelId());
        session.setSrcApp(SRC_APP);
        session.setSrcStream(recvStreamId);
        session.setSsrc(deviceSsrc);
        session.setMode(GbBroadcastSession.MODE_BROADCAST);
        session.setStatus(GbBroadcastSession.STATUS_WAIT_INVITE);
        session.setRecvStreamId(recvStreamId);
        session.setRecvServerId(server.getServerId());
        session.setRecvPort(recvPort);
        session.setRecvSsrc(recvSsrc);
        session.setParentPlatformId(String.valueOf(platform.getId()));
        session.setParentCallId(callId.getCallId());
        session.setParentFromTag(fromTag);
        session.setCreateTime(LocalDateTime.now());
        sessionService.save(session);

        try {
            String sdpIp = server.getStreamIp() != null && !server.getStreamIp().isBlank()
                    ? server.getStreamIp() : server.getHostIp();
            String sdp = SdpBuilder.buildAudioRecvonlyOffer(properties.getId(), sdpIp, recvPort, recvSsrc);
            Request invite = headerProvider.createInviteToUpstream(
                    platform, upstreamChannelId, recvSsrc, sdp, callId, fromTag);
            sipSender.send(headerProvider.localIp(), invite);
            log.info("[{}] 已主动 INVITE 上级收音频 上级:{} 通道:{} 收流端口:{} callId:{}",
                    LOG_TAG, platform.getServerGbId(), channel.getChannelId(), recvPort, callId.getCallId());
        } catch (Exception e) {
            log.error("[{}] INVITE 上级失败 上级:{} 错误:{}",
                    LOG_TAG, platform.getServerGbId(), e.getMessage(), e);
            failBeforeDialog(session, server);
        }
    }

    /**
     * 上级回 2xx：存 to-tag、发 ACK，再通知下级设备。
     */
    public void onUpstreamInviteOk(SIPResponse response) {
        CallIdHeader callIdHeader = (CallIdHeader) response.getHeader(CallIdHeader.NAME);
        String callId = callIdHeader == null ? null : callIdHeader.getCallId();
        GbBroadcastSession session = callId == null ? null : sessionService.getByParentCallId(callId);
        if (session == null) {
            return;
        }
        gb28181BizExecutor.execute(() -> handleInviteOk(platformOf(session), session, response));
    }

    private void handleInviteOk(GbParentPlatform platform, GbBroadcastSession session, SIPResponse response) {
        if (platform == null) {
            log.warn("[{}] 上级平台配置已删除，放弃 流:{}", LOG_TAG, session.getBroadcastStreamId());
            return;
        }
        ToHeader to = (ToHeader) response.getHeader(ToHeader.NAME);
        String toTag = to == null ? null : to.getTag();
        if (toTag == null) {
            log.warn("[{}] 上级 200 OK 缺 to-tag，放弃 流:{}", LOG_TAG, session.getBroadcastStreamId());
            failBeforeDialog(session, mediaServerOf(session));
            return;
        }
        session.setParentToTag(toTag);
        sessionService.update(session);

        String remoteHost;
        int remotePort;
        if (response.getPeerPacketSourceAddress() != null) {
            remoteHost = response.getPeerPacketSourceAddress().getHostAddress();
            remotePort = response.getPeerPacketSourcePort();
        } else {
            ViaHeader via = response.getTopmostViaHeader();
            remoteHost = via.getReceived() != null ? via.getReceived() : via.getHost();
            remotePort = via.getRPort() != -1 ? via.getRPort() : via.getPort();
        }
        try {
            Request ack = headerProvider.buildAckToUpstream(platform, response, remoteHost, remotePort);
            sipSender.send(headerProvider.localIp(), ack);
            log.info("[{}] 已给上级发 ACK 上级:{} 流:{}",
                    LOG_TAG, platform.getServerGbId(), session.getBroadcastStreamId());
        } catch (Exception e) {
            log.error("[{}] 给上级发 ACK 失败 流:{} 错误:{}",
                    LOG_TAG, session.getBroadcastStreamId(), e.getMessage(), e);
            gbBroadcastService.cleanupCascade(session, false, false);
            return;
        }

        // 上级腿已建立，开始通知设备；失败时 cleanup 会 BYE 上级
        try {
            gbBroadcastService.startCascadeDeviceLeg(session);
        } catch (Exception e) {
            log.error("[{}] 下级设备广播通道建立失败 流:{} 错误:{}",
                    LOG_TAG, session.getBroadcastStreamId(), e.getMessage(), e);
            gbBroadcastService.cleanupCascade(session, false, true);
        }
    }

    /**
     * 上级回非 2xx：Dialog 未建立，只释放本地资源。
     */
    public void onUpstreamInviteFail(String callId, int status) {
        GbBroadcastSession session = sessionService.getByParentCallId(callId);
        if (session == null) {
            return;
        }
        log.warn("[{}] 上级拒绝反向 INVITE status:{} 流:{}",
                LOG_TAG, status, session.getBroadcastStreamId());
        gbBroadcastService.cleanupCascade(session, false, false);
    }

    /**
     * 上级主动 BYE：停设备腿并清理，不再 BYE 上级。
     */
    public void onUpstreamBye(GbBroadcastSession session) {
        if (session == null) return;
        gbBroadcastService.cleanupCascade(session, true, false);
        log.info("[{}] 上级停喊清理完成 流:{}", LOG_TAG, session.getBroadcastStreamId());
    }

    /** 上级 Dialog 建立前失败：关收流端口、释放 SSRC、删会话，不发 BYE */
    private void failBeforeDialog(GbBroadcastSession session, MediaServer server) {
        sessionService.remove(session.getBroadcastStreamId());
        if (server != null) {
            zlmRestClient.closeRtpServer(server, session.getRecvStreamId());
        }
        if (session.getServerId() != null) {
            if (session.getSsrc() != null) {
                ssrcFactory.release(session.getServerId(), session.getSsrc());
            }
            if (session.getRecvSsrc() != null) {
                ssrcFactory.release(session.getServerId(), session.getRecvSsrc());
            }
        }
    }

    private GbParentPlatform platformOf(GbBroadcastSession session) {
        if (session.getParentPlatformId() == null) {
            return null;
        }
        return gbBroadcastService.loadPlatform(session.getParentPlatformId());
    }

    private MediaServer mediaServerOf(GbBroadcastSession session) {
        return gbBroadcastService.loadMediaServer(
                session.getRecvServerId() != null ? session.getRecvServerId() : session.getServerId());
    }

    /**
     * 按上级通知的通道码反查已共享通道：先查内部码加共享校验，再查共享暴露码。
     */
    private GbDeviceChannel resolveChannel(GbParentPlatform platform, String deviceId) {
        if (platform.getId() == null) {
            return null;
        }
        Long platformId = platform.getId();

        GbDeviceChannel channel = channelMapper.selectOne(
                new LambdaQueryWrapper<GbDeviceChannel>().eq(GbDeviceChannel::getChannelId, deviceId));
        if (channel != null && channel.getId() != null) {
            GbPlatformShare share = shareMapper.selectOne(new LambdaQueryWrapper<GbPlatformShare>()
                    .eq(GbPlatformShare::getPlatformId, platformId)
                    .eq(GbPlatformShare::getResourceId, channel.getId())
                    .last("LIMIT 1"));
            if (share != null) {
                return channel;
            }
        }

        GbPlatformShare share = shareMapper.selectOne(new LambdaQueryWrapper<GbPlatformShare>()
                .eq(GbPlatformShare::getPlatformId, platformId)
                .eq(GbPlatformShare::getExposedCode, deviceId)
                .last("LIMIT 1"));
        if (share == null || share.getResourceId() == null) {
            return null;
        }
        return channelMapper.selectById(share.getResourceId());
    }
}
