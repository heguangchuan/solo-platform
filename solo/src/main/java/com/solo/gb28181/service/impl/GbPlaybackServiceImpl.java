package com.solo.gb28181.service.impl;

import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.config.MediaProperties;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.entity.GbMediaServer;
import com.solo.gb28181.media.MediaUrlBuilder;
import com.solo.gb28181.media.client.ZlmRestClient;
import com.solo.gb28181.media.ssrc.SsrcFactory;
import com.solo.gb28181.request.PlaybackControlRequest;
import com.solo.gb28181.request.PlaybackStartRequest;
import com.solo.gb28181.request.PlaybackStopRequest;
import com.solo.gb28181.request.RecordInfoQueryRequest;
import com.solo.gb28181.response.PlaybackResultResponse;
import com.solo.gb28181.response.RecordItemResponse;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.service.GbPlaybackService;
import com.solo.gb28181.sip.SipLayer;
import com.solo.gb28181.transmit.cmd.RtspControlBuilder;
import com.solo.gb28181.transmit.cmd.SdpBuilder;
import com.solo.gb28181.transmit.cmd.SipCommander;
import com.solo.gb28181.transmit.cmd.SipRequestHeaderProvider;
import com.solo.gb28181.utils.SipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sip.header.CallIdHeader;
import java.time.LocalDateTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 历史回放编排实现
 *
 * <h3>关键流程</h3>
 *
 * <p><b>RecordInfo 查询</b>：</p>
 * <ol>
 *   <li>service 生成 SN，注册 {@code CompletableFuture<List<RecordItemResponse>>} 到 {@link #pendingQuery}</li>
 *   <li>调 {@link SipCommander#queryRecordInfo} 发 MESSAGE</li>
 *   <li>设备 Response 分片由 {@code RecordInfoResponseMessageHandler} 收集，
 *       达到 SumNum 时调 {@link #onRecordInfoReceived} 唤醒 future</li>
 *   <li>service 同步 get future（{@code recordInfoTimeoutSeconds} 秒超时）</li>
 * </ol>
 *
 * <p><b>Playback INVITE</b>：跟点播流程基本一致，差异点：</p>
 * <ul>
 *   <li>SDP 用 {@link SdpBuilder#buildPlayback}（s=Playback、t=&lt;start&gt; &lt;end&gt;）</li>
 *   <li>SSRC 用 {@link SsrcFactory#acquire}({@code true}) 切到 D=1 段</li>
 *   <li>streamId 加时间段后缀：{@code <dev>_<chan>_<startCompact>_<endCompact>}</li>
 *   <li>sessionType = {@link GbInviteSession#TYPE_PLAYBACK}</li>
 *   <li>session 上记录 {@code startTimeStr / endTimeStr}（Unix 秒字符串）</li>
 * </ul>
 *
 * <p><b>回放控制</b>：复用既有 INVITE Dialog 发 SIP INFO + MANSRTSP body
 * （{@link RtspControlBuilder}）。本地不维护回放进度，跳转/变速立即下发，画面接收后即生效。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbPlaybackServiceImpl implements GbPlaybackService {

    private static final String APP = "rtp";
    private static final String LOG_TAG = "历史回放";
    /** 紧凑时间格式，用于拼 streamId */
    private static final DateTimeFormatter COMPACT_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** UI 传入的时间格式 */
    private static final DateTimeFormatter UI_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** ISO-8601 给 RecordInfo XML 用 */
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final Gb28181Properties sipProperties;
    private final MediaProperties mediaProperties;
    private final SipLayer sipLayer;
    private final GbInviteSessionService inviteSessionService;
    private final SsrcFactory ssrcFactory;
    private final ZlmRestClient zlmRestClient;
    private final SipCommander sipCommander;
    private final SipRequestHeaderProvider headerProvider;
    private final MediaUrlBuilder urlBuilder;
    private final GbInviteSupport support;

    /** RecordInfo 查询等待表，key = {@code deviceId + ":" + sn} */
    private final Map<String, CompletableFuture<List<RecordItemResponse>>> pendingQuery = new ConcurrentHashMap<>();
    /** 回放 INVITE 等待表，key = streamId */
    private final Map<String, CompletableFuture<PlaybackResultResponse>> pendingPlay = new ConcurrentHashMap<>();

    // ============================================================
    //  录像查询
    // ============================================================

    @Override
    public List<RecordItemResponse> queryRecordInfo(RecordInfoQueryRequest req) {
        if (req == null || req.getDeviceId() == null || req.getChannelId() == null
                || req.getStartTime() == null || req.getEndTime() == null) {
            throw new ServiceException("deviceId / channelId / startTime / endTime 不能为空");
        }
        GbDevice device = support.device(req.getDeviceId());
        if (device == null) {
            throw new ServiceException("设备不存在: " + req.getDeviceId());
        }
        if (device.getStatus() == null || device.getStatus() != 1) {
            throw new ServiceException("设备离线，无法查询录像");
        }
        GbDeviceChannel channel = support.channel(req.getDeviceId(), req.getChannelId());
        if (channel == null) {
            throw new ServiceException("通道不存在: " + req.getChannelId());
        }

        // SN 生成 + 注册 future
        int sn = SipCommander.nextSn();
        String pendingKey = pendingKey(req.getDeviceId(), sn);
        CompletableFuture<List<RecordItemResponse>> future = new CompletableFuture<>();
        pendingQuery.put(pendingKey, future);

        try {
            // 把 UI 的 "yyyy-MM-dd HH:mm:ss" 转成 ISO-8601 "yyyy-MM-ddTHH:mm:ss"
            String startIso = toIso(req.getStartTime());
            String endIso = toIso(req.getEndTime());
            sipCommander.queryRecordInfo(device, req.getChannelId(), sn, startIso, endIso);
            log.info("[{}] 查询录像 deviceId={} channelId={} sn={} {} ~ {}",
                    LOG_TAG, req.getDeviceId(), req.getChannelId(), sn, startIso, endIso);
        } catch (Exception e) {
            pendingQuery.remove(pendingKey);
            throw new ServiceException("查询录像异常: " + e.getMessage());
        }

        try {
            return future.get(mediaProperties.getRecordInfoTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pendingQuery.remove(pendingKey);
            // 设备超时未回 Response：多数是设备无录像能力 / 不支持 RecordInfo（不回任何报文）。
            // 返回空列表而非抛错——前端展示"无录像"即可，避免误导成"系统故障"。
            log.warn("[{}] 录像查询超时（设备未响应，按无录像处理）deviceId={} sn={}",
                    LOG_TAG, req.getDeviceId(), sn);
            return Collections.emptyList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pendingQuery.remove(pendingKey);
            throw new ServiceException("等待被中断");
        } catch (ExecutionException e) {
            pendingQuery.remove(pendingKey);
            throw new ServiceException(e.getCause() == null ? "录像查询失败" : e.getCause().getMessage());
        }
    }

    @Override
    public void onRecordInfoReceived(String deviceId, int sn, List<RecordItemResponse> items) {
        CompletableFuture<List<RecordItemResponse>> f = pendingQuery.remove(pendingKey(deviceId, sn));
        if (f != null && !f.isDone()) {
            f.complete(items == null ? Collections.emptyList() : items);
        }
    }

    // ============================================================
    //  开始 / 停止回放
    // ============================================================

    @Override
    public PlaybackResultResponse start(PlaybackStartRequest req) {
        if (req == null || req.getDeviceId() == null || req.getChannelId() == null
                || req.getStartTime() == null || req.getEndTime() == null) {
            throw new ServiceException("deviceId / channelId / startTime / endTime 不能为空");
        }
        GbDevice device = support.device(req.getDeviceId());
        if (device == null) {
            throw new ServiceException("设备不存在: " + req.getDeviceId());
        }
        if (device.getStatus() == null || device.getStatus() != 1) {
            throw new ServiceException("设备离线，无法回放");
        }
        GbDeviceChannel channel = support.channel(req.getDeviceId(), req.getChannelId());
        if (channel == null) {
            throw new ServiceException("通道不存在: " + req.getChannelId());
        }

        // 时间段解析
        LocalDateTime startDt = LocalDateTime.parse(req.getStartTime(), UI_FMT);
        LocalDateTime endDt = LocalDateTime.parse(req.getEndTime(), UI_FMT);
        long startEpoch = startDt.atZone(ZoneId.systemDefault()).toEpochSecond();
        long endEpoch = endDt.atZone(ZoneId.systemDefault()).toEpochSecond();
        if (endEpoch <= startEpoch) {
            throw new ServiceException("结束时间必须晚于起始时间");
        }

        GbMediaServer server = support.pickMediaServer();
        String transport = support.normalizeTransport(req.getTransport());
        // streamId 带时间段后缀，避免不同时段并发回放同一通道冲突
        String streamId = req.getDeviceId() + "_" + req.getChannelId()
                + "_" + startDt.format(COMPACT_FMT) + "_" + endDt.format(COMPACT_FMT);

        // 同 streamId 已存在 → 复用 / 拒绝
        GbInviteSession exist = inviteSessionService.getByStreamId(streamId);
        if (exist != null) {
            if (GbInviteSession.STATUS_READY.equals(exist.getStatus())) {
                log.info("[{}] 复用已就绪会话 stream={}", LOG_TAG, streamId);
                return toResult(exist, startEpoch, endEpoch);
            }
            if (GbInviteSession.STATUS_INVITING.equals(exist.getStatus())) {
                log.warn("[{}] 发现残留 INVITING 会话 stream={}，强制清扫后重新点播", LOG_TAG, streamId);
                forceCleanup(exist);
            }
        }

        String ssrc = ssrcFactory.acquire(server.getServerId(), true);
        int rtpPort;
        try {
            int tcpMode = SdpBuilder.TRANSPORT_TCP_PASSIVE.equalsIgnoreCase(transport) ? 1 : 0;
            rtpPort = support.openRtpServerWithRetry(server, streamId, ssrc, tcpMode);
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
        session.setDeviceId(req.getDeviceId());
        session.setChannelId(req.getChannelId());
        session.setSsrc(ssrc);
        session.setRtpPort(rtpPort);
        session.setTransport(transport);
        session.setCallId(callId);
        session.setFromTag(fromTag);
        session.setSessionType(GbInviteSession.TYPE_PLAYBACK);
        session.setStartTimeStr(String.valueOf(startEpoch));
        session.setEndTimeStr(String.valueOf(endEpoch));
        session.setStatus(GbInviteSession.STATUS_INVITING);
        session.setStartTime(LocalDateTime.now());
        session.setRecord(mediaProperties.isRecordSip());
        inviteSessionService.save(session);

        CompletableFuture<PlaybackResultResponse> future = new CompletableFuture<>();
        pendingPlay.put(streamId, future);

        try {
            String sdpIp = server.getStreamIp() != null && !server.getStreamIp().isBlank()
                    ? server.getStreamIp() : server.getHostIp();
            String sdpBody = SdpBuilder.buildPlayback(sipProperties.getId(), sdpIp, rtpPort, ssrc,
                    transport, startEpoch, endEpoch);
            long inviteTimeoutMs = Math.max(5_000L, mediaProperties.getPlaybackTimeoutSeconds() * 1000L);

            sipCommander.sendInvite(device, req.getChannelId(), sdpBody, ssrc, callIdHeader, fromTag,
                    okEv -> log.debug("[{}] 收到 INVITE 2xx stream={}", LOG_TAG, streamId),
                    errEv -> {
                        int code = errEv == null || errEv.getResponse() == null
                                ? -1 : errEv.getResponse().getStatusCode();
                        support.failSession(session, LOG_TAG, "INVITE 失败 status=" + code);
                        failPending(streamId, "INVITE 失败 status=" + code);
                    },
                    inviteTimeoutMs);
        } catch (Exception e) {
            pendingPlay.remove(streamId);
            support.failSession(session, LOG_TAG, "INVITE 异常: " + e.getMessage());
            throw new ServiceException("INVITE 异常: " + e.getMessage());
        }

        try {
            return future.get(mediaProperties.getPlaybackTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pendingPlay.remove(streamId);
            support.timeoutSession(session, LOG_TAG, "等待流就绪超时");
            throw new ServiceException("回放超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pendingPlay.remove(streamId);
            support.failSession(session, LOG_TAG, "等待被中断");
            throw new ServiceException("等待被中断");
        } catch (ExecutionException e) {
            pendingPlay.remove(streamId);
            Throwable cause = e.getCause();
            if (cause instanceof ServiceException se) {
                throw se;
            }
            throw new ServiceException(cause == null ? "回放失败" : cause.getMessage());
        }
    }

    @Override
    public void stop(PlaybackStopRequest req) {
        if (req == null || req.getStreamId() == null) {
            throw new ServiceException("streamId 不能为空");
        }
        GbInviteSession session = inviteSessionService.getByStreamId(req.getStreamId());
        if (session == null) {
            log.info("[{}] stop: stream={} 无对应会话（已清扫）", LOG_TAG, req.getStreamId());
            return;
        }
        GbDevice device = support.device(session.getDeviceId());
        if (device != null && session.getCallId() != null && session.getFromTag() != null
                && session.getToTag() != null) {
            sipCommander.sendBye(device, session);
        } else {
            log.warn("[{}] stop 时设备/Dialog 信息不全，跳过 BYE stream={}", LOG_TAG, session.getStreamId());
        }
        support.cleanupZlm(session);
        inviteSessionService.removeByStreamId(session.getStreamId());
        failPending(session.getStreamId(), "会话已停止");
        log.info("[{}] stop ok stream={}", LOG_TAG, session.getStreamId());
    }

    // ============================================================
    //  回放控制：SIP INFO + MANSRTSP
    // ============================================================

    @Override
    public void control(PlaybackControlRequest req) {
        if (req == null || req.getStreamId() == null || req.getAction() == null) {
            throw new ServiceException("streamId / action 不能为空");
        }
        GbInviteSession session = inviteSessionService.getByStreamId(req.getStreamId());
        if (session == null) {
            throw new ServiceException("会话不存在: " + req.getStreamId());
        }
        if (!GbInviteSession.TYPE_PLAYBACK.equals(session.getSessionType())) {
            throw new ServiceException("非回放会话不支持控制");
        }
        if (!GbInviteSession.STATUS_READY.equals(session.getStatus())) {
            throw new ServiceException("流尚未就绪，无法控制");
        }
        GbDevice device = support.device(session.getDeviceId());
        if (device == null) {
            throw new ServiceException("设备已离线");
        }

        String body;
        switch (req.getAction()) {
            case "pause" -> body = RtspControlBuilder.pause();
            case "resume" -> body = RtspControlBuilder.resume();
            case "seek" -> {
                long sec = parseLong(req.getParam(), -1);
                if (sec < 0) throw new ServiceException("seek 参数不合法: " + req.getParam());
                body = RtspControlBuilder.seek(sec);
            }
            case "speed" -> {
                double speed = parseDouble(req.getParam(), 1.0);
                body = RtspControlBuilder.scale(speed);
            }
            default -> throw new ServiceException("不支持的 action: " + req.getAction());
        }
        sipCommander.sendInfo(device, session, body);
        log.info("[回放控制] action={} param={} stream={}", req.getAction(), req.getParam(), req.getStreamId());
    }

    // ============================================================
    //  Hook 钩子（ZlmHookService 按 streamId 反查 sessionType 决定路由到此处还是 GbPlayService）
    // ============================================================

    @Override
    public void onInviteOk(String callId, String remoteSsrc) {
        if (callId == null || callId.isBlank()) return;
        GbInviteSession session = inviteSessionService.getByCallId(callId);
        if (session == null) return;
        if (remoteSsrc == null || remoteSsrc.isBlank() || remoteSsrc.equals(session.getSsrc())) return;

        log.warn("[{}] SSRC 不一致 local={} remote={} stream={}，尝试改写 ZLM 过滤值",
                LOG_TAG, session.getSsrc(), remoteSsrc, session.getStreamId());
        GbMediaServer server = support.mediaServer(session.getServerId());
        if (server == null) {
            log.error("[{}] SSRC 自适应失败：节点不存在 serverId={}", LOG_TAG, session.getServerId());
            support.failAndBye(session, LOG_TAG, "节点丢失");
            failPending(session.getStreamId(), "节点丢失");
            return;
        }
        boolean ok = zlmRestClient.updateRtpServerSSRC(server, session.getStreamId(), remoteSsrc);
        if (!ok) {
            log.error("[{}] ZLM updateRtpServerSSRC 失败，主动 BYE stream={}", LOG_TAG, session.getStreamId());
            support.failAndBye(session, LOG_TAG, "ZLM updateRtpServerSSRC 失败");
            failPending(session.getStreamId(), "ZLM updateRtpServerSSRC 失败");
            return;
        }
        String oldSsrc = session.getSsrc();
        ssrcFactory.release(session.getServerId(), oldSsrc);
        session.setSsrc(remoteSsrc);
        inviteSessionService.update(session);
        log.info("[{}] SSRC 自适应成功 stream={} {} → {}",
                LOG_TAG, session.getStreamId(), oldSsrc, remoteSsrc);
    }

    @Override
    public void onStreamReady(String app, String stream, String mediaServerId) {
        if (!APP.equals(app)) return;
        GbInviteSession session = inviteSessionService.getByStreamId(stream);
        if (session == null) {
            log.warn("[{}] on_stream_changed 找不到会话 stream={}", LOG_TAG, stream);
            return;
        }
        if (GbInviteSession.STATUS_READY.equals(session.getStatus())) {
            return;
        }
        GbMediaServer server = support.mediaServer(session.getServerId());
        if (server == null) {
            log.error("[{}] on_stream_changed 节点丢失 serverId={}", LOG_TAG, session.getServerId());
            return;
        }
        session.setStatus(GbInviteSession.STATUS_READY);
        session.setReadyTime(LocalDateTime.now());
        session.setFlvUrl(urlBuilder.buildFlv(server, APP, stream));
        session.setWsFlvUrl(urlBuilder.buildWsFlv(server, APP, stream));
        session.setHlsUrl(urlBuilder.buildHls(server, APP, stream));
        session.setRtcUrl(urlBuilder.buildRtc(server, APP, stream));
        inviteSessionService.update(session);

        log.info("[{}] 流就绪 stream={} wsFlv={}", LOG_TAG, stream, session.getWsFlvUrl());
        CompletableFuture<PlaybackResultResponse> f = pendingPlay.remove(stream);
        if (f != null) {
            long start = parseLong(session.getStartTimeStr(), 0);
            long end = parseLong(session.getEndTimeStr(), 0);
            f.complete(toResult(session, start, end));
        }
    }

    @Override
    public void onStreamStopped(String app, String stream, String mediaServerId) {
        if (!APP.equals(app)) return;
        GbInviteSession session = inviteSessionService.getByStreamId(stream);
        if (session == null) return;
        // 跟点播一样：ZLM 端流断了，主动给设备发 BYE（设备可能还在推流）
        GbDevice device = support.device(session.getDeviceId());
        if (device != null && session.getToTag() != null) {
            sipCommander.sendBye(device, session);
        }
        support.cleanupZlm(session);
        inviteSessionService.removeByStreamId(session.getStreamId());
        log.info("[{}] 流断开清扫完成 stream={}", LOG_TAG, stream);
    }

    @Override
    public void onDeviceBye(GbInviteSession session) {
        if (session == null) return;
        support.cleanupZlm(session);
        inviteSessionService.removeByStreamId(session.getStreamId());
        failPending(session.getStreamId(), "设备主动 BYE");
    }

    @Override
    public void onRtpServerTimeout(String streamId) {
        if (streamId == null || streamId.isBlank()) return;
        GbInviteSession session = inviteSessionService.getByStreamId(streamId);
        if (session == null) return;
        GbDevice device = support.device(session.getDeviceId());
        if (device != null && session.getToTag() != null) {
            sipCommander.sendBye(device, session);
        }
        // ZLM 这边端口本来就超时了，不需要再 closeRtpServer
        ssrcFactory.release(session.getServerId(), session.getSsrc());
        inviteSessionService.removeByStreamId(streamId);
        failPending(streamId, "RTP 端口长时间无数据");
        log.warn("[{}] RTP 端口超时清扫完成 stream={}", LOG_TAG, streamId);
    }

    // ============================================================
    //  helpers
    // ============================================================

    private void forceCleanup(GbInviteSession session) {
        support.forceCleanup(session);
        failPending(session.getStreamId(), "会话被强清");
    }

    private void failPending(String streamId, String reason) {
        CompletableFuture<PlaybackResultResponse> f = pendingPlay.remove(streamId);
        if (f != null && !f.isDone()) {
            f.completeExceptionally(new ServiceException(reason));
        }
    }

    private static String pendingKey(String deviceId, int sn) {
        return deviceId + ":" + sn;
    }

    private static String toIso(String uiTime) {
        return LocalDateTime.parse(uiTime, UI_FMT).format(ISO_FMT);
    }

    private static long parseLong(String s, long def) {
        if (s == null || s.isBlank()) return def;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static double parseDouble(String s, double def) {
        if (s == null || s.isBlank()) return def;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private PlaybackResultResponse toResult(GbInviteSession session, long startEpoch, long endEpoch) {
        PlaybackResultResponse vo = new PlaybackResultResponse();
        vo.setStreamId(session.getStreamId());
        vo.setApp(APP);
        vo.setMediaServerId(session.getServerId());
        vo.setSsrc(session.getSsrc());
        vo.setRtpPort(session.getRtpPort());
        vo.setTransport(session.getTransport());
        vo.setFlvUrl(session.getFlvUrl());
        vo.setWsFlvUrl(session.getWsFlvUrl());
        vo.setHlsUrl(session.getHlsUrl());
        vo.setRtcUrl(session.getRtcUrl());
        vo.setStartEpochSec(startEpoch);
        vo.setEndEpochSec(endEpoch);
        vo.setDurationSec(endEpoch - startEpoch);
        return vo;
    }
}
