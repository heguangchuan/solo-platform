package com.solo.media.hook;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.service.GbCloudRecordService;
import com.solo.gb28181.service.GbDownloadService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.service.GbRecordFileService;
import com.solo.gb28181.service.GbPlayService;
import com.solo.gb28181.service.GbPlaybackService;
import com.solo.gb28181.service.impl.CascadeSendService;
import com.solo.gb28181.service.impl.GbBroadcastService;
import com.solo.media.entity.MediaServer;
import com.solo.media.entity.GbViewRecord;
import com.solo.media.entity.StreamProxy;
import com.solo.media.entity.StreamPush;
import com.solo.media.entity.StreamPushClient;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.mapper.StreamPushClientMapper;
import com.solo.media.properties.MediaProperties;
import com.solo.media.service.GbViewRecordService;
import com.solo.media.service.MediaServerLoader;
import com.solo.media.service.StreamProxyService;
import com.solo.media.service.StreamPushService;
import com.solo.media.support.PlayTarget;
import com.solo.media.support.ViewTokenSigner;
import com.solo.onvif.service.OnvifPlayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// secret 在 Controller 验过了。普通业务异常仍回 code=0，免得 ZLM 一直重试；鉴权失败不能这么放。
@Slf4j
@Service
@RequiredArgsConstructor
public class ZlmHookService {

    private final MediaProperties mediaProperties;
    private final GbMediaServerMapper mediaServerMapper;
    private final StreamPushClientMapper streamPushClientMapper;
    private final MediaServerLoader loadIndex;
    private final GbInviteSessionService inviteSessionService;
    private final GbPlayService playService;
    private final GbPlaybackService playbackService;
    private final GbDownloadService downloadService;
    private final GbCloudRecordService cloudRecordService;
    private final GbRecordFileService recordFileService;
    private final StreamPushService streamPushService;
    private final StreamProxyService streamProxyService;
    private final CascadeSendService cascadeSendService;
    private final GbBroadcastService gbBroadcastService;
    private final OnvifPlayService onvifPlayService;
    private final GbViewRecordService viewRecordService;
    private final ViewTokenSigner viewTokenSigner;
    private final InternalPlayAuthorizer internalPlayAuthorizer;


    public HookResult onServerKeepalive(Map<String, Object> body) {
        String serverId = stringOf(body, "mediaServerId");
        if (serverId == null) {
            log.warn("[ZLM 回调] on_server_keepalive 缺少 mediaServerId: {}", body);
            return HookResult.ok();
        }
        loadIndex.onNodeOnline(serverId);
        int rows = mediaServerMapper.update(null, new LambdaUpdateWrapper<MediaServer>()
                .eq(MediaServer::getServerId, serverId)
                .set(MediaServer::getStatus, 1)
                .set(MediaServer::getHookAliveTime, LocalDateTime.now()));
        if (rows == 0) {
            log.warn("[ZLM 回调] on_server_keepalive 但节点未登记 serverId={}", serverId);
        }
        return HookResult.ok();
    }

    public HookResult onServerStarted(Map<String, Object> body) {
        String serverId = stringOf(body, "mediaServerId");
        log.info("[ZLM 回调] on_server_started serverId={}", serverId);
        if (serverId == null) return HookResult.ok();

        loadIndex.onNodeOnline(serverId);

        MediaServer exist = mediaServerMapper.selectOne(
                new LambdaQueryWrapper<MediaServer>().eq(MediaServer::getServerId, serverId));
        if (exist == null) {
            log.warn("[ZLM 回调] on_server_started 但节点未在 gb_media_server 登记 serverId={}（请在管理面新增）", serverId);
        } else {
            exist.setStatus(1);
            exist.setHookAliveTime(LocalDateTime.now());
            mediaServerMapper.updateById(exist);
        }
        try {
            streamPushService.reconcileWithZlm(serverId);
        } catch (Exception e) {
            log.warn("[ZLM 回调] on_server_started 推流兜底同步异常 serverId={} err={}", serverId, e.getMessage());
        }
        try {
            streamProxyService.reconcileWithZlm(serverId);
        } catch (Exception e) {
            log.warn("[ZLM 回调] on_server_started 拉流代理兜底异常 serverId={} err={}", serverId, e.getMessage());
        }
        return HookResult.ok();
    }


    public HookResult onPlay(Map<String, Object> body) {
        String id = stringOf(body, "id");
        String app = stringOf(body, "app");
        String stream = stringOf(body, "stream");
        log.info("[ZLM 回调] on_play id={} app={} stream={} schema={}", id, app, stream, stringOf(body, "schema"));
        PlayAuthorization authorization = authorizePlay(body);
        if (authorization == null) {
            return rejectPlay(body);
        }
        if (!authorization.internalReader() && mediaProperties.getViewRecord().isEnabled()) {
            try {
                // 观看记录写失败别影响已经通过的播放
                viewRecordService.openRecord(buildViewRecord(body, id, authorization.token()));
            } catch (Exception e) {
                log.warn("[ZLM 回调] on_play 观看记录落库失败 stream={} err={}", stream, e.getMessage());
            }
        }
        return HookResult.ok();
    }

    public HookResult onFlowReport(Map<String, Object> body) {
        String id = stringOf(body, "id");
        String stream = stringOf(body, "stream");
        log.info("[ZLM 回调] on_flow_report id={} stream={} duration={}s player={}",
                id, stream, stringOf(body, "duration"), body.get("player"));
        try {
            // player=false 是设备推流腿（RtpProcess 的标识就是流名），观看记录只认播放会话
            if (!Boolean.TRUE.equals(booleanOf(body, "player"))) {
                return HookResult.ok();
            }
            // 字段名按 ZLM master 实际报文：duration 整数秒、totalBytes 驼峰，没有上下行拆分
            double duration = doubleOf(body, "duration");
            viewRecordService.closeBySessionId(id,
                    duration > 0 ? duration : null,
                    longBoxed(body, "totalBytes"),
                    GbViewRecordService.REASON_FLOW_REPORT);
        } catch (Exception e) {
            log.warn("[ZLM 回调] on_flow_report 闭合失败 id={} err={}", id, e.getMessage());
        }
        return HookResult.ok();
    }

    // HLS 的 ts/m4s 等文件也得带同一枚 wt，不能只守住 m3u8
    public HookResult onHttpAccess(Map<String, Object> body) {
        String path = stringOf(body, "path");
        if (hasHlsSuffix(path) && (path.indexOf('\\') >= 0 || path.contains(".."))) {
            return rejectPlay(body);
        }
        HlsPathTarget target = liveHlsTarget(path);
        if (target == null) {
            return HookResult.ok();
        }
        String mediaServerId = stringOf(body, "mediaServerId");
        String wt = parseParam(stringOf(body, "params"), ViewTokenSigner.PARAM);
        try {
            // record 也能被用户拿来当直播 app，确认不是直播后才按录像文件放行
            if (target.recordPath() && !isLiveRecordApp(target.stream(), mediaServerId)) {
                return HookResult.ok();
            }
            if (mediaServerId == null || mediaServerId.isBlank() || wt == null || wt.isBlank()
                    || viewTokenSigner.verify(wt, PlayTarget.hls(mediaServerId, target.app(), target.stream())) == null) {
                return rejectPlay(body);
            }
        } catch (Exception e) {
            log.warn("[播放鉴权] HLS 文件校验异常，按拒绝处理 path={} err={}",
                    stringOf(body, "path"), e.getMessage());
            return rejectPlay(body);
        }
        return HookResult.ok();
    }

    private HlsPathTarget liveHlsTarget(String path) {
        if (!hasHlsSuffix(path)) {
            return null;
        }
        String cleanPath = path.split("\\?", 2)[0].replaceAll("/+", "/");
        String[] segments = cleanPath.startsWith("/") ? cleanPath.substring(1).split("/") : cleanPath.split("/");
        if (segments.length < 3 || segments[0].isBlank() || segments[1].isBlank()) {
            return null;
        }
        return new HlsPathTarget(segments[0], segments[1], "record".equalsIgnoreCase(segments[0]));
    }

    private boolean isLiveRecordApp(String stream, String mediaServerId) {
        if (mediaServerId == null || mediaServerId.isBlank()) {
            return true;
        }
        long proxies = streamProxyService.count(new LambdaQueryWrapper<StreamProxy>()
                .eq(StreamProxy::getApp, "record")
                .eq(StreamProxy::getStream, stream)
                .eq(StreamProxy::getMediaServerId, mediaServerId)
                .eq(StreamProxy::getStatus, 1));
        if (proxies > 0) {
            return true;
        }
        // stream_push 只保存当前活动推流，注销时会删除记录。
        return streamPushService.count(new LambdaQueryWrapper<StreamPush>()
                .eq(StreamPush::getApp, "record")
                .eq(StreamPush::getStream, stream)
                .eq(StreamPush::getMediaServerId, mediaServerId)) > 0;
    }

    private static boolean hasHlsSuffix(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String lowerPath = path.split("\\?", 2)[0].toLowerCase(java.util.Locale.ROOT);
        return lowerPath.endsWith(".m3u8") || lowerPath.endsWith(".ts")
                || lowerPath.endsWith(".m4s") || lowerPath.endsWith(".mp4");
    }

    private record HlsPathTarget(String app, String stream, boolean recordPath) {
    }

    private GbViewRecord buildViewRecord(Map<String, Object> body, String id, ViewTokenSigner.ViewToken token) {
        if (id == null) {
            return null;
        }
        GbViewRecord record = new GbViewRecord();
        record.setSessionId(id);
        record.setActorType(token.actorType());
        record.setActorId(token.actorId());
        record.setActorName(token.actorName());
        record.setStreamId(token.streamId());
        record.setApp(token.app());
        record.setStreamSchema(token.schema());
        record.setClientIp(stringOf(body, "ip"));
        record.setMediaServerId(token.mediaServerId());
        record.setStartTime(LocalDateTime.now());
        // 设备/通道从点播会话补；查不到（外部推流的播放）就留空，流维度的统计不受影响
        GbInviteSession session = inviteSessionService.getByStreamId(token.streamId());
        if (session != null) {
            record.setDeviceId(session.getDeviceId());
            record.setChannelId(session.getChannelId());
        }
        return record;
    }

    private PlayAuthorization authorizePlay(Map<String, Object> body) {
        try {
            return doAuthorizePlay(body);
        } catch (Exception e) {
            // Redis、密钥或报文出错也拒绝，不能让异常变成放行
            log.warn("[播放鉴权] 校验异常，按拒绝处理 server={} app={} stream={} err={}",
                    stringOf(body, "mediaServerId"), stringOf(body, "app"), stringOf(body, "stream"), e.getMessage());
            return null;
        }
    }

    private PlayAuthorization doAuthorizePlay(Map<String, Object> body) {
        String id = stringOf(body, "id");
        String mediaServerId = stringOf(body, "mediaServerId");
        String app = stringOf(body, "app");
        String stream = stringOf(body, "stream");
        String schema = stringOf(body, "schema");
        if (isAnyBlank(mediaServerId, app, stream, schema)) {
            return null;
        }
        String wt = parseParam(stringOf(body, "params"), ViewTokenSigner.PARAM);
        if (wt == null || wt.isBlank()) {
            return internalPlayAuthorizer.isAuthorized(id, mediaServerId, app, stream, schema)
                    ? PlayAuthorization.internal() : null;
        }
        ViewTokenSigner.ViewToken token = verifyPlayToken(wt, mediaServerId, app, stream, schema,
                stringOf(body, "protocol"));
        return token == null ? null : PlayAuthorization.external(token);
    }

    private ViewTokenSigner.ViewToken verifyPlayToken(String wt, String mediaServerId, String app,
                                                       String stream, String schema, String protocol) {
        if (PlayTarget.SCHEMA_RTMP.equalsIgnoreCase(schema) && (protocol == null || protocol.isBlank())) {
            // 这版 ZLM 的 FLV 回调不给 protocol，只能按 HTTP/WS 两种目标验，别当成报文异常
            return viewTokenSigner.verifyAny(wt, List.of(
                    PlayTarget.httpFlv(mediaServerId, app, stream),
                    PlayTarget.wsFlv(mediaServerId, app, stream)));
        }
        return viewTokenSigner.verify(wt, new PlayTarget(mediaServerId, app, stream, schema, protocol));
    }

    private HookResult rejectPlay(Map<String, Object> body) {
        logPlayReject(body);
        return HookResult.rejectPlay("play unauthorized");
    }

    private HookResult rejectStreamNotFound(Map<String, Object> body) {
        logPlayReject(body);
        return HookResult.closeNotFoundStream("play unauthorized");
    }

    private void logPlayReject(Map<String, Object> body) {
        log.warn("[播放鉴权] 拒绝播放 server={} app={} stream={} schema={} ip={}",
                stringOf(body, "mediaServerId"), stringOf(body, "app"), stringOf(body, "stream"),
                stringOf(body, "schema"), stringOf(body, "ip"));
    }

    private static boolean isAnyBlank(String... values) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private record PlayAuthorization(ViewTokenSigner.ViewToken token, boolean internalReader) {
        private static PlayAuthorization external(ViewTokenSigner.ViewToken token) {
            return new PlayAuthorization(token, false);
        }

        private static PlayAuthorization internal() {
            return new PlayAuthorization(null, true);
        }
    }

    public HookResult onStreamNotFound(Map<String, Object> body) {
        String app = stringOf(body, "app");
        String stream = stringOf(body, "stream");
        String mediaServerId = stringOf(body, "mediaServerId");
        log.info("[ZLM 回调] on_stream_not_found app={} stream={} schema={}",
                app, stream, stringOf(body, "schema"));

        PlayAuthorization authorization = authorizePlay(body);
        if (authorization == null) {
            return rejectStreamNotFound(body);
        }
        // 内部 reader 能读已有源，但不能借这个 hook 去 INVITE 设备
        if (authorization.internalReader()) {
            return HookResult.ok();
        }

        // 只给国标主码流做按需点播
        if (mediaProperties.isAutoApplyPlay() && "rtp".equals(app)) {
            // 主码流名是 deviceId_channelId；子码流后缀反推不回厂商 token，只能让前端显式 start
            String[] parts = stream.split("_");
            if (parts.length == 2) {
                String deviceId = parts[0];
                String channelId = parts[1];
                log.info("[ZLM 回调] on_stream_not_found 已鉴权，自动发起点播 deviceId={} channelId={}", deviceId, channelId);
                // 异步拉流，别卡住 ZLM 回调
                playService.autoStartPlay(deviceId, channelId, mediaServerId);
            } else if (parts.length > 2) {
                log.info("[ZLM 回调] on_stream_not_found 子码流流名 stream={}，按需拉流跳过（需前端显式点播）", stream);
            }
        }
        return HookResult.ok();
    }

    public HookResult onPublish(Map<String, Object> body) {
        log.info("[ZLM 回调] on_publish {}", body);

        String app = stringOf(body, "app");
        String stream = stringOf(body, "stream");

        // rtp/broadcast/talk 是平台自己发起的，其他 app 才查推流 token
        boolean isPlatformInternalApp = "rtp".equals(app) || "broadcast".equals(app) || "talk".equals(app);
        if (mediaProperties.isPushAuthEnabled() && !isPlatformInternalApp) {
            String token = parseParam(stringOf(body, "params"), "token");
            String clientId = authenticate(token);
            if (clientId == null) {
                log.warn("[推流鉴权] 拒绝 app={} stream={} ip={} tokenPrefix={} 原因={}",
                        app, stream, stringOf(body, "ip"), maskToken(token),
                        token == null ? "缺少 token" : "token 无效或已禁用");
                return HookResult.rejectPublish("push token invalid");
            }
            log.info("[推流鉴权] 通过 app={} stream={} clientId={}", app, stream, clientId);
        }

        return HookResult.ok().put("enable_audio", true);
    }

    public HookResult onStreamChanged(Map<String, Object> body) {
        String schema = stringOf(body, "schema");
        String earlyApp = stringOf(body, "app");
        // 语音广播 / 语音对讲的浏览器推流生命周期由广播 service 自己管：流就绪事件转交它唤醒等待中的
        // startSendRtp（替代忙等轮询），流断开的清扫仍由下面的 onStreamStopped 分支处理。
        if ("broadcast".equals(earlyApp) || "talk".equals(earlyApp)) {
            if (Boolean.TRUE.equals(booleanOf(body, "regist"))) {
                gbBroadcastService.onStreamReady(earlyApp, stringOf(body, "stream"), stringOf(body, "mediaServerId"));
            }
            return HookResult.ok();
        }
        // 同一路流的 ts/rtsp/rtmp/fmp4 都会各调一次，这里只处理 rtsp，别重复计数
        if (!"rtsp".equalsIgnoreCase(schema)) return HookResult.ok();

        Boolean regist = booleanOf(body, "regist");
        String app = stringOf(body, "app");
        String stream = stringOf(body, "stream");
        String mediaServerId = stringOf(body, "mediaServerId");
        log.info("[ZLM 回调] on_stream_changed app={} stream={} regist={}", app, stream, regist);

        // 先记负载，再走业务侧（业务侧可能因会话缺失早 return，但分数仍然反映 ZLM 上的真实流数）
        if (Boolean.TRUE.equals(regist)) {
            loadIndex.onStreamArrival(mediaServerId);
        } else if (Boolean.FALSE.equals(regist)) {
            loadIndex.onStreamDeparture(mediaServerId);
            // ONVIF 到这时代理已经没了，只清 Redis，别再调 delStreamProxy
            if (onvifPlayService.isOnvifStream(app, stream)) {
                try {
                    onvifPlayService.cleanupByStreamId(stream);
                } catch (Exception e) {
                    log.warn("[ZLM 回调] ONVIF 钩子清扫失败 stream={} err={}", stream, e.getMessage());
                }
            }
        }

        try {
            String sessionType = sessionTypeOf(stream);
            if (Boolean.TRUE.equals(regist)) {
                if (sessionType == null) {
                    // rtp/recv_* 不在点播会话里，是对讲回传流
                    if (gbBroadcastService.ownsStream(app, stream)
                            || gbBroadcastService.ownsRecvStream(app, stream)) {
                        gbBroadcastService.onStreamReady(app, stream, mediaServerId);
                    } else {
                        playService.onStreamReady(app, stream, mediaServerId);
                    }
                } else {
                    switch (sessionType) {
                        case GbInviteSession.TYPE_DOWNLOAD -> downloadService.onStreamReady(app, stream, mediaServerId);
                        case GbInviteSession.TYPE_RECORD -> cloudRecordService.onStreamReady(app, stream, mediaServerId);
                        case GbInviteSession.TYPE_PLAYBACK -> playbackService.onStreamReady(app, stream, mediaServerId);
                        default -> playService.onStreamReady(app, stream, mediaServerId);
                    }
                }
                streamPushService.onStreamRegister(app, stream, mediaServerId, body);
            } else {
                if (sessionType == null) {
                    playService.onStreamStopped(app, stream, mediaServerId);
                } else {
                    switch (sessionType) {
                        case GbInviteSession.TYPE_DOWNLOAD -> downloadService.onStreamStopped(app, stream, mediaServerId);
                        case GbInviteSession.TYPE_RECORD -> cloudRecordService.onStreamStopped(app, stream, mediaServerId);
                        case GbInviteSession.TYPE_PLAYBACK -> playbackService.onStreamStopped(app, stream, mediaServerId);
                        default -> playService.onStreamStopped(app, stream, mediaServerId);
                    }
                }
                streamPushService.onStreamCancel(app, stream, mediaServerId);
                // 流都没了，没等到 flow_report 的观看会话在这里一次性兜底
                try {
                    viewRecordService.closeOpenByStreamId(stream, GbViewRecordService.REASON_STREAM_CLOSED);
                } catch (Exception ex) {
                    log.warn("[ZLM 回调] 观看记录流级兜底失败 stream={} err={}", stream, ex.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[ZLM 回调] on_stream_changed 业务异常 stream={}", stream, e);
        }
        return HookResult.ok();
    }

    public HookResult onStreamNoneReader(Map<String, Object> body) {
        String app = stringOf(body, "app");
        String stream = stringOf(body, "stream");
        String originTypeStr = stringOf(body, "originTypeStr");
        boolean isOnvif = onvifPlayService.isOnvifStream(app, stream);

        // 语音广播：浏览器 WebRTC 推上来的音频流本来就没有播放者（平台只做 startSendRtp 转出），
        // 绝不能被无人观看回收，否则喊话中途就断了。对讲同理。
        if ("broadcast".equals(app) || "talk".equals(app)) {
            return HookResult.ok().put("close", false);
        }
        // 对讲设备回传流 rtp/recv_*：浏览器播放腿建立前没有读者，也不能被提前回收
        if (gbBroadcastService.ownsRecvStream(app, stream)) {
            return HookResult.ok().put("close", false);
        }

        // 外部推流（rtsp/rtmp/rtc push）不入"无人观看即关"：关掉之后外部源必须重推，
        // 平台无能为力。判定依据：stream_push 表里有这行 = 外部推流
        // （GB INVITE 走 rtp app 不入此表，ONVIF 拉流代理走 stream_proxy 也不入此表）。
        // ZLM 在 on_stream_none_reader 事件里 originTypeStr 不可靠（实测为 null），
        // 故以本表为准。ZLM 自带的分钟级流超时是兜底，不依赖本钩子早关。
        if (app != null && stream != null
                && streamPushService.lambdaQuery()
                        .eq(StreamPush::getApp, app)
                        .eq(StreamPush::getStream, stream)
                        .exists()) {
            log.info("[ZLM 回调] on_stream_none_reader app={} stream={} -> 外部推流，不关闭", app, stream);
            return HookResult.ok().put("close", false);
        }

        // 云端录像流没有播放端是常态（只录制不分发），绝不能因无人观看被关，
        // 否则计划流刚被对账拉起就被 ZLM 回收。录像流统一以 REC_ 前缀命名。
        GbInviteSession session = inviteSessionService.getByStreamId(stream);
        if (session != null && GbInviteSession.TYPE_RECORD.equals(session.getSessionType())) {
            log.info("[ZLM 回调] on_stream_none_reader app={} stream={} -> 云端录像流，保持不关", app, stream);
            return HookResult.ok().put("close", false);
        }

        // 其余平台自起的流（GB INVITE 点播/回放 / ONVIF 拉流代理 / 兜底）：无人观看立即关。
        // GB 重连代价是重新走 SIP INVITE（几百 ms），ONVIF 重连代价是再 addStreamProxy，
        // 都可接受。关流后 ZLM 内部会发 on_stream_changed(regist=false)，由对应服务的 cleanup 兜底。
        boolean close = true;

        // 最后一个观看者走了：flow_report 通常已经先到并闭合，这里只收没等到回调的漏网记录
        try {
            viewRecordService.closeOpenByStreamId(stream, GbViewRecordService.REASON_NONE_READER);
        } catch (Exception ex) {
            log.warn("[ZLM 回调] 观看记录无人观看兜底失败 stream={} err={}", stream, ex.getMessage());
        }

        log.info("[ZLM 回调] on_stream_none_reader app={} stream={} originType={} 会话在册={} onvif={} -> close={}",
                app, stream, originTypeStr, session != null, isOnvif, close);
        return HookResult.ok().put("close", close);
    }

    public HookResult onRtpServerTimeout(Map<String, Object> body) {
        String streamId = stringOf(body, "stream_id");
        log.warn("[ZLM 回调] on_rtp_server_timeout stream_id={}", streamId);
        try {
            // sessionType 为空时先走默认分支，别直接 switch(null)
            String sessionType = sessionTypeOf(streamId);
            if (sessionType == null) {
                playService.onRtpServerTimeout(streamId);
            } else {
                switch (sessionType) {
                    case GbInviteSession.TYPE_DOWNLOAD -> downloadService.onRtpServerTimeout(streamId);
                    case GbInviteSession.TYPE_RECORD -> cloudRecordService.onRtpServerTimeout(streamId);
                    case GbInviteSession.TYPE_PLAYBACK -> playbackService.onRtpServerTimeout(streamId);
                    default -> playService.onRtpServerTimeout(streamId);
                }
            }
        } catch (Exception e) {
            log.error("[ZLM 回调] on_rtp_server_timeout 业务异常 stream_id={}", streamId, e);
        }
        return HookResult.ok();
    }

    private String sessionTypeOf(String streamId) {
        if (streamId == null) return null;
        GbInviteSession s = inviteSessionService.getByStreamId(streamId);
        return s == null ? null : s.getSessionType();
    }

    public HookResult onRecordMp4(Map<String, Object> body) {
        String app = stringOf(body, "app");
        String stream = stringOf(body, "stream");
        String url = stringOf(body, "url");
        // path 是 ZLM 本机绝对路径，全局唯一，作为 hook 重推去重的 key；url 是相对下载地址
        String path = stringOf(body, "path");
        // start_time 为切片开始的 unix 秒；老版本/异常情况可能给 0，业务侧再回退估算
        long startRaw = longOf(body, "start_time");
        Long startTime = startRaw > 0 ? startRaw : null;
        long fileSize = longOf(body, "file_size");
        double duration = doubleOf(body, "time_len");
        log.info("[ZLM 回调] on_record_mp4 app={} stream={} url={} fileSize={} duration={}s",
                app, stream, url, fileSize, duration);
        if (stream == null) {
            return HookResult.ok();
        }
        GbInviteSession session = inviteSessionService.getByStreamId(stream);
        try {
            if (session == null) {
                // 停止录制会先删 INVITE 会话，尾片由独立录像档案确认归属
                recordFileService.onRecordMp4(app, stream, url, path, startTime, fileSize, duration);
                return HookResult.ok();
            }
            String type = session.getSessionType();
            if (GbInviteSession.TYPE_DOWNLOAD.equals(type)) {
                downloadService.onRecordMp4(app, stream, url, fileSize, duration);
            } else if (GbInviteSession.TYPE_RECORD.equals(type)) {
                recordFileService.onRecordMp4(app, stream, url, path, startTime, fileSize, duration);
            } else {
                log.info("[ZLM 回调] on_record_mp4 非录制/下载流，跳过 type={} stream={}", type, stream);
            }
        } catch (Exception e) {
            log.error("[ZLM 回调] on_record_mp4 业务异常 stream={}", stream, e);
        }
        return HookResult.ok();
    }

    // 只用 MP4；TS 回调也得应一声，不然 ZLM 会一直刷 404
    public HookResult onRecordTs(Map<String, Object> body) {
        if (log.isDebugEnabled()) {
            log.debug("[ZLM 回调] on_record_ts 忽略 stream={}", body == null ? null : body.get("stream"));
        }
        return HookResult.ok();
    }

    public HookResult onSendRtpStopped(Map<String, Object> body) {
        log.info("[ZLM 回调] on_send_rtp_stopped {}", body);
        try {
            String stream = stringOf(body, "stream");
            String ssrc = stringOf(body, "ssrc");
            if (stream != null) {
                cascadeSendService.onSendRtpStopped(stream, ssrc);
                // 广播的 startSendRtp 也走这个回调
                try {
                    gbBroadcastService.onSendRtpStopped(stream, ssrc);
                } catch (Exception ex) {
                    log.error("[ZLM 回调] on_send_rtp_stopped 广播清扫异常 stream={} ssrc={}", stream, ssrc, ex);
                }
            }
        } catch (Exception e) {
            log.error("[ZLM 回调] on_send_rtp_stopped 业务异常 {}", body, e);
        }
        return HookResult.ok();
    }

    private String authenticate(String token) {
        if (token == null || token.isBlank()) return null;
        StreamPushClient hit = streamPushClientMapper.selectOne(
                new LambdaQueryWrapper<StreamPushClient>()
                        .eq(StreamPushClient::getToken, token)
                        .eq(StreamPushClient::getEnabled, 1));
        return hit == null ? null : hit.getClientId();
    }


    private static String stringOf(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v == null ? null : v.toString();
    }

    private static Boolean booleanOf(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }

    private static long longOf(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString().trim()); } catch (NumberFormatException e) { return 0L; }
    }

    // 这里要分清“没上报”和“上报 0”
    private static Long longBoxed(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString().trim()); } catch (NumberFormatException e) { return null; }
    }

    private static double doubleOf(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null) return 0d;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString().trim()); } catch (NumberFormatException e) { return 0d; }
    }

    // ZLM 会把 params 里的 = 和 & 转成 HTML 实体，拆参数前先解回来
    private static String parseParam(String params, String key) {
        if (params == null || params.isEmpty() || key == null) return null;
        String decoded = decodeHtmlEntities(params);
        for (String pair : decoded.split("&")) {
            int idx = pair.indexOf('=');
            if (idx <= 0) continue;
            if (key.equals(pair.substring(0, idx))) {
                return pair.substring(idx + 1);
            }
        }
        return null;
    }

    // 只解 ZLM 这里会出现的几种实体，不为此引个 HTML 库
    private static String decodeHtmlEntities(String s) {
        if (s == null || s.indexOf('&') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c != '&') { sb.append(c); i++; continue; }
            int semi = s.indexOf(';', i + 1);
            if (semi < 0 || semi - i > 8) { sb.append(c); i++; continue; }
            String ent = s.substring(i + 1, semi);
            char decoded;
            try {
                if (ent.startsWith("#x") || ent.startsWith("#X")) {
                    decoded = (char) Integer.parseInt(ent.substring(2), 16);
                } else if (ent.startsWith("#")) {
                    decoded = (char) Integer.parseInt(ent.substring(1));
                } else {
                    decoded = switch (ent) {
                        case "amp" -> '&';
                        case "lt" -> '<';
                        case "gt" -> '>';
                        case "quot" -> '"';
                        case "apos" -> '\'';
                        default -> 0;
                    };
                    if (decoded == 0) { sb.append(c); i++; continue; }
                }
            } catch (NumberFormatException e) {
                sb.append(c);
                i++;
                continue;
            }
            sb.append(decoded);
            i = semi + 1;
        }
        return sb.toString();
    }

    private static String maskToken(String token) {
        if (token == null) return "null";
        if (token.length() < 6) return "***";
        return token.substring(0, 6) + "***";
    }
}
