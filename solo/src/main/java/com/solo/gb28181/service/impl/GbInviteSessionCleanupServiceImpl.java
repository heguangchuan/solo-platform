package com.solo.gb28181.service.impl;

import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.service.GbInviteSessionCleanupService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.transmit.cmd.SipCommander;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.service.MediaServerService;
import com.solo.media.ssrc.SsrcFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * INVITE 会话清扫实现，点播 / 回放 / 下载共用。
 *
 * <p>S3 引入 — 原先散在 {@code GbPlayServiceImpl} 里的私有 helper 抽到这里，
 * 让 {@code GbPlaybackServiceImpl} 也能复用，避免大段重复。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbInviteSessionCleanupServiceImpl implements GbInviteSessionCleanupService {

    private final GbDeviceService deviceService;
    private final MediaServerService mediaServerService;
    private final GbInviteSessionService inviteSessionService;
    private final SsrcFactory ssrcFactory;
    private final ZlmRestClient zlmRestClient;
    private final SipCommander sipCommander;

    @Override
    public int openRtpServerWithRetry(MediaServer server, String streamId, int tcpMode) {
        try {
            return zlmRestClient.openRtpServer(server, streamId, tcpMode, 0);
        } catch (ServiceException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (!msg.contains("already exists")) {
                throw e;
            }
            log.warn("[流媒体] ZLM 检测到 stream={} 残留，强制 closeRtpServer 后重试", streamId);
            zlmRestClient.closeRtpServer(server, streamId);
            return zlmRestClient.openRtpServer(server, streamId, tcpMode, 0);
        }
    }

    @Override
    public void cleanupZlm(GbInviteSession session) {
        try {
            MediaServer server = mediaServerService.lambdaQuery()
                    .eq(MediaServer::getServerId, session.getServerId())
                    .one();
            if (server != null) {
                zlmRestClient.closeRtpServer(server, session.getStreamId());
            }
        } catch (Exception e) {
            log.warn("[流媒体] closeRtpServer 异常 stream={} {}", session.getStreamId(), e.getMessage());
        }
        ssrcFactory.release(session.getServerId(), session.getSsrc());
    }

    @Override
    public void forceCleanup(GbInviteSession session) {
        cleanupZlm(session);
        inviteSessionService.removeByStreamId(session.getStreamId());
    }

    @Override
    public void failSession(GbInviteSession session, String logTag, String reason) {
        cleanupZlm(session);
        inviteSessionService.removeByStreamId(session.getStreamId());
        log.warn("[{}] 会话失败 stream={} reason={}", logTag, session.getStreamId(), reason);
    }

    @Override
    public void failAndBye(GbInviteSession session, String logTag, String reason) {
        sendByeIfDialogEstablished(session);
        cleanupZlm(session);
        inviteSessionService.removeByStreamId(session.getStreamId());
        log.warn("[{}] 会话被动放弃 stream={} reason={}", logTag, session.getStreamId(), reason);
    }

    @Override
    public void timeoutSession(GbInviteSession session, String logTag, String reason) {
        cleanupZlm(session);
        sendByeIfDialogEstablished(session);
        inviteSessionService.removeByStreamId(session.getStreamId());
        log.warn("[{}] 会话超时 stream={} reason={}", logTag, session.getStreamId(), reason);
    }

    /**
     * 仅当 Dialog 已建立（收到过 200 OK，即有 to-tag）时才发 BYE——
     * 没有 to-tag 的 BYE 对端会直接丢弃，发了也没用。
     */
    private void sendByeIfDialogEstablished(GbInviteSession session) {
        if (session.getToTag() == null) {
            return;
        }
        GbDevice device = deviceService.lambdaQuery()
                .eq(GbDevice::getDeviceId, session.getDeviceId())
                .one();
        if (device != null) {
            sipCommander.sendBye(device, session);
        }
    }
}
