package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.service.GbCloudRecordService;
import com.solo.gb28181.service.GbInviteSessionCleanupService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.transmit.cmd.SdpBuilder;
import com.solo.gb28181.transmit.cmd.SipCommander;
import com.solo.gb28181.transmit.cmd.SipRequestHeaderProvider;
import com.solo.gb28181.utils.SipUtils;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.properties.MediaProperties;
import com.solo.media.service.MediaServerSelector;
import com.solo.media.ssrc.SsrcFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sip.header.CallIdHeader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * <h3>云端录制会话编排</h3>
 * <ol>
 *   <li>{@link #startPlan} / {@link #startManual}：校验设备通道 → 选节点 → 申请 SSRC/开 RTP 端口
 *       → 存 {@link GbInviteSession}(TYPE_RECORD, INVITING) → 发实时 INVITE（和点播同一码流池）</li>
 *   <li>on_stream_changed(regist=true) → {@link #onStreamReady}：调 ZLM startRecord 开 MP4 切片。
 *       max_second 给一个保底值，正常切片靠 ZLM 跨天/流停时自动收尾</li>
 *   <li>ZLM 每切出一段 MP4 → on_record_mp4 → {@link GbRecordFileServiceImpl} 落 S3</li>
 *   <li>{@link #stop} / 设备 BYE / 流断 / RTP 超时：发 BYE（尽量）→ 关端口 → 释放 SSRC → 删会话</li>
 * </ol>
 *
 * <h3>为什么计划录制的 streamId 要加 REC 前缀</h3>
 * <p>点播 streamId 是裸 {@code deviceId_channelId}，计划录制和点播经常同时存在（边录边看）。
 * 共用流名会让 ZLM openRtpServer 撞车，也会让 hook 路由分不清会话类型。REC 前缀让两路流
 * 在 ZLM 里各自独立，代价是设备会被同一通道 INVITE 两次，主流设备都支持。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbCloudRecordServiceImpl implements GbCloudRecordService {

    private static final String LOG_TAG = "云端录制";
    private static final String APP = "rtp";
    /** startRecord 的保底单段时长：6h。跨天切片 ZLM 自己会切，这里只是防异常情况下文件无限长 */
    private static final int MAX_SEGMENT_SECONDS = 6 * 3600;

    private final Gb28181Properties sipProperties;
    private final MediaProperties mediaProperties;
    private final SsrcFactory ssrcFactory;
    private final ZlmRestClient zlmRestClient;
    private final SipCommander sipCommander;
    private final SipRequestHeaderProvider headerProvider;
    private final MediaServerSelector mediaServerSelector;
    private final GbMediaServerMapper mediaServerMapper;
    private final GbDeviceMapper deviceMapper;
    private final GbDeviceChannelMapper channelMapper;
    private final GbInviteSessionService inviteSessionService;
    private final GbInviteSessionCleanupService inviteCleanup;

    @PostConstruct
    void cleanupOnStart() {
        // 重启后 Redis 里残留的录制会话全部作废：SIP dialog 已不存在，等对账调度按计划重新拉起
        List<GbInviteSession> active = inviteSessionService.listAllActive().stream()
                .filter(s -> GbInviteSession.TYPE_RECORD.equals(s.getSessionType()))
                .toList();
        for (GbInviteSession s : active) {
            inviteSessionService.removeByStreamId(s.getStreamId());
        }
        if (!active.isEmpty()) {
            log.info("[{}] 启动清扫：{} 条残留录制会话已删除", LOG_TAG, active.size());
        }
    }

    // ============================================================
    //  开始 / 停止
    // ============================================================

    @Override
    public String startManual(String deviceId, String channelId, String transport, String streamNumber) {
        return start(deviceId, channelId, transport, streamNumber, false);
    }

    @Override
    public String startPlan(String deviceId, String channelId, String transport, String streamNumber) {
        return start(deviceId, channelId, transport, streamNumber, true);
    }

    private String start(String deviceId, String channelId, String transport,
                         String streamNumber, boolean fromPlan) {
        if (deviceId == null || channelId == null) {
            throw new ServiceException("deviceId / channelId 不能为空");
        }
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, deviceId));
        if (device == null) {
            throw new ServiceException("设备不存在: " + deviceId);
        }
        if (device.getStatus() == null || device.getStatus() != 1) {
            throw new ServiceException("设备离线，无法录制");
        }
        GbDeviceChannel channel = channelMapper.selectOne(
                new LambdaQueryWrapper<GbDeviceChannel>()
                        .eq(GbDeviceChannel::getDeviceId, deviceId)
                        .eq(GbDeviceChannel::getChannelId, channelId));
        if (channel == null) {
            throw new ServiceException("通道不存在: " + channelId);
        }
        if (!"ON".equalsIgnoreCase(channel.getStatus())) {
            throw new ServiceException("通道离线，无法录制");
        }

        String streamId = fromPlan
                ? GbCloudRecordService.planStreamId(deviceId, channelId, streamNumber)
                : GbCloudRecordService.manualStreamId(deviceId, channelId, streamNumber);
        String normalizedToken = GbCloudRecordService.normalizeToken(streamNumber);

        // 幂等：已在录（READY/INVITING）直接复用，对账和手动接口每分钟可能重复打到这里
        GbInviteSession existing = inviteSessionService.getByStreamId(streamId);
        if (existing != null) {
            log.info("[{}] 会话已存在，复用 流ID:{} 状态:{}", LOG_TAG, streamId, existing.getStatus());
            return streamId;
        }

        MediaServer server = mediaServerSelector.pick();
        String useTransport = SdpBuilder.normalizeTransport(transport);
        // 录制是实时流，和点播共用实时 SSRC 段（回放/下载才用 playback 池）
        String ssrc = ssrcFactory.acquire(server.getServerId(), false);
        int rtpPort;
        try {
            rtpPort = inviteCleanup.openRtpServerWithRetry(server, streamId, transportToTcpMode(useTransport));
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
        session.setDeviceId(deviceId);
        session.setChannelId(channelId);
        session.setSsrc(ssrc);
        session.setRtpPort(rtpPort);
        session.setTransport(useTransport);
        session.setStreamNumber(normalizedToken);
        session.setCallId(callId);
        session.setFromTag(fromTag);
        session.setSessionType(GbInviteSession.TYPE_RECORD);
        session.setStatus(GbInviteSession.STATUS_INVITING);
        session.setStartTime(LocalDateTime.now());
        inviteSessionService.save(session);

        try {
            // SDP c= 行必须是设备能直推到的 ZLM 地址（streamIp 优先回退 hostIp），跟点播一个道理
            String sdpIp = server.getStreamIp() != null && !server.getStreamIp().isBlank()
                    ? server.getStreamIp() : server.getHostIp();
            String sdpBody = SdpBuilder.buildPlay(sipProperties.getId(), sdpIp, rtpPort, ssrc,
                    useTransport, rawStreamNumber(streamNumber));
            long inviteTimeoutMs = Math.max(5_000L, mediaProperties.getInviteTimeoutSeconds() * 1000L);

            sipCommander.sendInvite(device, channelId, sdpBody, ssrc, rtpPort, callIdHeader, fromTag,
                    okEv -> log.info("[{}] INVITE 2xx 到达 流ID:{}", LOG_TAG, streamId),
                    errEv -> {
                        int code = errEv == null || errEv.getResponse() == null
                                ? -1 : errEv.getResponse().getStatusCode();
                        log.warn("[{}] INVITE 拒绝 流ID:{} 状态码:{}", LOG_TAG, streamId, code);
                        inviteCleanup.failSession(session, LOG_TAG, "INVITE 失败 status=" + code);
                    },
                    inviteTimeoutMs);
        } catch (Exception e) {
            inviteCleanup.failSession(session, LOG_TAG, "INVITE 异常: " + e.getMessage());
            throw new ServiceException("INVITE 异常: " + e.getMessage());
        }

        log.info("[{}] 已发起录制 流ID:{} 来源:{} 设备:{} 通道:{}",
                LOG_TAG, streamId, fromPlan ? "计划" : "手动", deviceId, channelId);
        return streamId;
    }

    @Override
    public void stop(String streamId) {
        if (streamId == null) {
            return;
        }
        GbInviteSession session = inviteSessionService.getByStreamId(streamId);
        if (session == null) {
            log.info("[{}] stop 流ID:{} 无对应会话 原因:已清扫", LOG_TAG, streamId);
            return;
        }
        byeIfPossible(session);
        inviteCleanup.cleanupZlm(session);
        inviteSessionService.removeByStreamId(streamId);
        log.info("[{}] 停止录制 流ID:{}", LOG_TAG, streamId);
    }

    @Override
    public void stopPlanRecord(String deviceId, String channelId, String streamNumber) {
        // 计划流 streamId 带 REC 前缀；同一通道只可能有一路（对账保证）
        stop(GbCloudRecordService.planStreamId(deviceId, channelId, streamNumber));
    }

    // ============================================================
    //  ZLM Hook 回调
    // ============================================================

    @Override
    public void onStreamReady(String app, String stream, String mediaServerId) {
        if (!APP.equals(app)) {
            return;
        }
        GbInviteSession session = inviteSessionService.getByStreamId(stream);
        if (session == null || !GbInviteSession.TYPE_RECORD.equals(session.getSessionType())) {
            return;
        }
        if (GbInviteSession.STATUS_READY.equals(session.getStatus())) {
            return; // on_stream_changed 对多个 schema 可能重复，靠 hook 入口只放 rtsp，这里再兜一层
        }
        MediaServer server = mediaServerMapper.selectOne(
                new LambdaQueryWrapper<MediaServer>().eq(MediaServer::getServerId, session.getServerId()));
        if (server == null) {
            log.error("[{}] 流就绪但节点丢失 节点ID:{} 流ID:{}", LOG_TAG, session.getServerId(), stream);
            return;
        }
        // period 只影响 ZLM 切片文件的目录日期，按天切；跨天 ZLM 会自动换段
        String period = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        boolean ok = zlmRestClient.startRecord(server, APP, stream, period, MAX_SEGMENT_SECONDS);
        if (!ok) {
            log.warn("[{}] startRecord 失败，清扫 流ID:{}", LOG_TAG, stream);
            inviteCleanup.failSession(session, LOG_TAG, "ZLM 启动录制失败");
            return;
        }
        session.setStatus(GbInviteSession.STATUS_READY);
        session.setReadyTime(LocalDateTime.now());
        inviteSessionService.update(session);
        log.info("[{}] 流就绪，已开始切片 流ID:{}", LOG_TAG, stream);
    }

    @Override
    public void onStreamStopped(String app, String stream, String mediaServerId) {
        if (!APP.equals(app)) {
            return;
        }
        GbInviteSession session = inviteSessionService.getByStreamId(stream);
        if (session == null || !GbInviteSession.TYPE_RECORD.equals(session.getSessionType())) {
            return;
        }
        // 流已断：最后一段 MP4 的 on_record_mp4 可能在这个钩子之后才到，
        // 删会话会让那段录像找不到 session 而被丢弃。延迟 3 分钟再清，给 hook 留足时间。
        // 先打 STOPPED 标记：对账靠它识别"流已死，需要重拉"，同时延迟任务也只清 STOPPED 的，
        // 万一这期间又有一路同名流就绪（READY）不会被误删。
        session.setStatus(GbInviteSession.STATUS_STOPPED);
        session.setStopTime(LocalDateTime.now());
        inviteSessionService.update(session);
        log.info("[{}] 流断开，延迟清理会话 流ID:{}", LOG_TAG, stream);
        final GbInviteSession snapshot = session;
        try {
            Thread.startVirtualThread(() -> delayedCleanup(snapshot));
        } catch (Exception e) {
            // 虚拟线程起不来（极端环境）就同步清，最多丢最后一段
            log.warn("[{}] 延迟清理任务提交失败，直接清理 流ID:{}", LOG_TAG, stream);
            inviteCleanup.cleanupZlm(session);
            inviteSessionService.removeByStreamId(stream);
        }
    }

    private void delayedCleanup(GbInviteSession snapshot) {
        try {
            Thread.sleep(3 * 60_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        GbInviteSession current = inviteSessionService.getByStreamId(snapshot.getStreamId());
        if (current == null) {
            return;
        }
        // 只清"流断后一直没回来"的会话；READY 说明流被重新拉起，INVITING 说明重拉在途
        if (!GbInviteSession.STATUS_STOPPED.equals(current.getStatus())) {
            return;
        }
        inviteCleanup.cleanupZlm(current);
        inviteSessionService.removeByStreamId(current.getStreamId());
        log.info("[{}] 流断后延迟清理完成 流ID:{}", LOG_TAG, current.getStreamId());
    }

    @Override
    public void onRtpServerTimeout(String streamId) {
        if (streamId == null || streamId.isBlank()) {
            return;
        }
        GbInviteSession session = inviteSessionService.getByStreamId(streamId);
        if (session == null || !GbInviteSession.TYPE_RECORD.equals(session.getSessionType())) {
            return;
        }
        log.warn("[{}] RTP 端口超时 流ID:{}", LOG_TAG, streamId);
        byeIfPossible(session);
        // 端口 ZLM 已自行回收，只需释放 SSRC + 删会话；计划流由对账下一分钟重新拉起
        ssrcFactory.release(session.getServerId(), session.getSsrc());
        inviteSessionService.removeByStreamId(streamId);
    }

    @Override
    public void onDeviceBye(GbInviteSession session) {
        if (session == null) {
            return;
        }
        // 同 onStreamStopped：最后一段切片回调可能还没到，延迟清
        log.info("[{}] 设备主动 BYE 流ID:{}", LOG_TAG, session.getStreamId());
        final GbInviteSession snapshot = session;
        try {
            Thread.startVirtualThread(() -> delayedCleanup(snapshot));
        } catch (Exception e) {
            inviteCleanup.cleanupZlm(session);
            inviteSessionService.removeByStreamId(session.getStreamId());
        }
    }

    // ============================================================
    //  helpers
    // ============================================================

    private void byeIfPossible(GbInviteSession session) {
        if (session.getCallId() == null || session.getFromTag() == null || session.getToTag() == null) {
            return;
        }
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, session.getDeviceId()));
        if (device != null) {
            sipCommander.sendBye(device, session);
        }
    }

    /** SDP a= 行允许冒号，写 SDP 时保留原值 */
    private static String rawStreamNumber(String token) {
        return token == null || token.isBlank() ? null : token.trim();
    }

    private static int transportToTcpMode(String transport) {
        if (SdpBuilder.TRANSPORT_TCP_PASSIVE.equalsIgnoreCase(transport)) return 1;
        if (SdpBuilder.TRANSPORT_TCP_ACTIVE.equalsIgnoreCase(transport)) return 2;
        return 0;
    }
}
