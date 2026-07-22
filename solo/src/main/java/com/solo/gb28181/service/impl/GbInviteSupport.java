package com.solo.gb28181.service.impl;

import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.media.entity.MediaServer;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.ssrc.SsrcFactory;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.media.service.MediaServerSelector;
import com.solo.media.service.MediaServerService;
import com.solo.gb28181.transmit.cmd.SdpBuilder;
import com.solo.gb28181.transmit.cmd.SipCommander;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 点播/回放公共支撑：环境清扫、节点选择、字段反查、SSRC + ZLM 端口配套清理。
 *
 * <p>S3 引入 — 之前 {@code GbPlayServiceImpl} 自有的私有 helper 现在挪到这里，
 * 让 {@code GbPlaybackServiceImpl} 也能复用，避免大段重复。
 *
 * <p>所有方法对 sessionType (PLAY / PLAYBACK / DOWNLOAD) 无关，纯工程性清扫。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GbInviteSupport {

    private final GbDeviceService deviceService;
    private final GbDeviceChannelService channelService;
    private final MediaServerService mediaServerService;
    private final MediaServerSelector mediaServerSelector;
    private final GbInviteSessionService inviteSessionService;
    private final SsrcFactory ssrcFactory;
    private final ZlmRestClient zlmRestClient;
    private final SipCommander sipCommander;

    // ---- 简单按字段查询的内部封装：避免每次都写 lambdaQuery().eq(...).one() ----

    public GbDevice device(String deviceId) {
        return deviceService.lambdaQuery().eq(GbDevice::getDeviceId, deviceId).one();
    }

    public GbDeviceChannel channel(String deviceId, String channelId) {
        return channelService.lambdaQuery()
                .eq(GbDeviceChannel::getDeviceId, deviceId)
                .eq(GbDeviceChannel::getChannelId, channelId)
                .one();
    }

    public MediaServer mediaServer(String serverId) {
        return mediaServerService.lambdaQuery().eq(MediaServer::getServerId, serverId).one();
    }

    // ---- 节点选择 / 入参规整 ----

    public MediaServer pickMediaServer() {
        return mediaServerSelector.pick();
    }

    public String normalizeTransport(String t) {
        if (t == null || t.isBlank()) return SdpBuilder.TRANSPORT_UDP;
        if (SdpBuilder.TRANSPORT_TCP_PASSIVE.equalsIgnoreCase(t)) return SdpBuilder.TRANSPORT_TCP_PASSIVE;
        return SdpBuilder.TRANSPORT_UDP;
    }

    // ---- ZLM 端口申请（带"流残留"重试） ----

    /**
     * 申请 RTP 端口；若 ZLM 报"stream already exists"，先 closeRtpServer 再开一次。
     */
    public int openRtpServerWithRetry(MediaServer server, String streamId, String ssrc, int tcpMode) {
        try {
            return zlmRestClient.openRtpServer(server, streamId, ssrc, tcpMode, 0);
        } catch (ServiceException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (!msg.contains("already exists")) {
                throw e;
            }
            log.warn("[流媒体] ZLM 检测到 stream={} 残留，强制 closeRtpServer 后重试", streamId);
            zlmRestClient.closeRtpServer(server, streamId);
            return zlmRestClient.openRtpServer(server, streamId, ssrc, tcpMode, 0);
        }
    }

    // ---- 失败/超时/正常停止 公共清扫 ----

    /**
     * 关 ZLM 的 RTP 端口 + 释放 SSRC（顺序严格：先关后释放，避免新会话拿到同 ssrc 时 openRtpServer 拒绝）。
     */
    public void cleanupZlm(GbInviteSession session) {
        try {
            MediaServer server = mediaServer(session.getServerId());
            if (server != null) {
                zlmRestClient.closeRtpServer(server, session.getStreamId());
            }
        } catch (Exception e) {
            log.warn("[流媒体] closeRtpServer 异常 stream={} {}", session.getStreamId(), e.getMessage());
        }
        ssrcFactory.release(session.getServerId(), session.getSsrc());
    }

    /**
     * 强制清扫一条残留会话（启动清扫 / 业务侧主动放弃）：先关 ZLM 端口 → 释放 SSRC → 删 session。
     * 不发 BYE（设备多半已经走完 Dialog 生命周期）。
     */
    public void forceCleanup(GbInviteSession session) {
        cleanupZlm(session);
        inviteSessionService.removeByStreamId(session.getStreamId());
    }

    /**
     * 失败放弃：不发 BYE（INVITE 都没成功，设备没有 Dialog）。
     */
    public void failSession(GbInviteSession session, String logTag, String reason) {
        cleanupZlm(session);
        inviteSessionService.removeByStreamId(session.getStreamId());
        log.warn("[{}] 会话失败 stream={} reason={}", logTag, session.getStreamId(), reason);
    }

    /**
     * 主动放弃 + 发 BYE（设备已收过 200 OK，必须 BYE 才会停推流）。
     */
    public void failAndBye(GbInviteSession session, String logTag, String reason) {
        GbDevice dev = device(session.getDeviceId());
        if (dev != null && session.getToTag() != null) {
            sipCommander.sendBye(dev, session);
        }
        cleanupZlm(session);
        inviteSessionService.removeByStreamId(session.getStreamId());
        log.warn("[{}] 会话被动放弃 stream={} reason={}", logTag, session.getStreamId(), reason);
    }

    /**
     * 等流超时（INVITE 已 200 OK 但流没来）：先 BYE 再清扫。
     */
    public void timeoutSession(GbInviteSession session, String logTag, String reason) {
        cleanupZlm(session);
        GbDevice dev = device(session.getDeviceId());
        if (dev != null && session.getToTag() != null) {
            sipCommander.sendBye(dev, session);
        }
        inviteSessionService.removeByStreamId(session.getStreamId());
        log.warn("[{}] 会话超时 stream={} reason={}", logTag, session.getStreamId(), reason);
    }
}
