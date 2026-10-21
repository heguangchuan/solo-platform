package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.service.GbInviteSessionCleanupService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.transmit.cmd.SipCommander;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.ssrc.SsrcFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * <p>S3 引入 — 原先散在 {@code GbPlayServiceImpl} 里的私有 helper 抽到这里，
 * 让 {@code GbPlaybackServiceImpl} 也能复用，避免大段重复。</p>
 *
 * <p>依赖说明：本类不直接依赖 {@code GbDeviceService} / {@code MediaServerService}，所有
 * 单表查（设备、节点）走 mapper。{@code GbInviteSessionService} 必须留——会话数据在 Redis，
 * 没有 mapper 等价实现。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbInviteSessionCleanupServiceImpl implements GbInviteSessionCleanupService {

    private final GbDeviceMapper deviceMapper;
    private final GbMediaServerMapper mediaServerMapper;
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
            MediaServer server = mediaServerMapper.selectOne(
                    new LambdaQueryWrapper<MediaServer>().eq(MediaServer::getServerId, session.getServerId()));
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
        log.warn("[{}] 会话失败 流ID:{} 原因:{}", logTag, session.getStreamId(), reason);
    }

    @Override
    public void failAndBye(GbInviteSession session, String logTag, String reason) {
        sendByeIfDialogEstablished(session);
        cleanupZlm(session);
        inviteSessionService.removeByStreamId(session.getStreamId());
        log.warn("[{}] 会话被动放弃 流ID:{} 原因:{}", logTag, session.getStreamId(), reason);
    }

    @Override
    public void timeoutSession(GbInviteSession session, String logTag, String reason) {
        cleanupZlm(session);
        sendByeIfDialogEstablished(session);
        inviteSessionService.removeByStreamId(session.getStreamId());
        log.warn("[{}] 会话超时 流ID:{} 原因:{}", logTag, session.getStreamId(), reason);
    }

    /**
     * 仅当 Dialog 已建立（收到过 200 OK，即有 to-tag）时才发 BYE——
     * 没有 to-tag 的 BYE 对端会直接丢弃，发了也没用。
     */
    private void sendByeIfDialogEstablished(GbInviteSession session) {
        if (session.getToTag() == null) {
            return;
        }
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, session.getDeviceId()));
        if (device != null) {
            sipCommander.sendBye(device, session);
        }
    }
}
