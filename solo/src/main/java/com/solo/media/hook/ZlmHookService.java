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
import com.solo.media.entity.StreamPush;
import com.solo.media.entity.StreamPushClient;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.mapper.StreamPushClientMapper;
import com.solo.media.properties.MediaProperties;
import com.solo.media.service.GbViewRecordService;
import com.solo.media.service.MediaServerLoader;
import com.solo.media.service.StreamProxyService;
import com.solo.media.service.StreamPushService;
import com.solo.media.support.ViewTokenSigner;
import com.solo.onvif.service.OnvifPlayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * <h3>职责</h3>
 * <ul>
 *   <li>消息字段解析（{@code stringOf / booleanOf}）</li>
 *   <li>按 streamId / 推流事件 / 服务事件等路由到对应业务 service</li>
 *   <li>节点级状态写回（status / hookAliveTime）：本类直接走 {@link GbMediaServerMapper}，
 *       不经 {@code MediaServerService}，避免 service-in-service</li>
 * </ul>
 *
 * <p>所有方法接收 ZLM 原始 body，返回 {@link HookResult}。Secret 校验由 {@link ZlmHookController}
 * 统一负责，业务方法内无需再判。</p>
 *
 * <h3>响应约定</h3>
 * <p>业务异常一律用日志承担并返 {@code code=0}，避免 ZLM 因 4xx/5xx 反复重试。</p>
 *
 * <h3>依赖说明</h3>
 * <p>本类作为 ZLM Hook 入口的编排层，<b>必须</b>调下列业务方法，因此直接依赖这些 service
 * （按项目规则，service 互相依赖的"业务编排"场景以"职责拆分不消解"为由允许，但应尽量集中到
 * 这种唯一编排点而非散落到多处）。每个 service 保留的业务调用点都注释了用途：</p>
 * <ul>
 *   <li>{@link GbInviteSessionService#getByStreamId} — 流级事件按 streamId 反查会话类型</li>
 *   <li>{@link GbPlayService} / {@link GbPlaybackService} — 实时 / 回放会话生命周期回调</li>
 *   <li>{@link StreamPushService} — 推流入表 + 推流兜底</li>
 *   <li>{@link CascadeSendService#onSendRtpStopped} — 级联推流异常断开清理</li>
 *   <li>{@link GbBroadcastService} — 语音广播推流异常断开清理（广播流在其他钩子中直接忽略/保活）</li>
 *   <li>{@link GbViewRecordService} — on_play 开观看记录、on_flow_report 闭合，流断/无人观看兜底</li>
 * </ul>
 */
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

    // ============ 服务级 ============

    public HookResult onServerKeepalive(Map<String, Object> body) {
        String serverId = stringOf(body, "mediaServerId");
        if (serverId == null) {
            log.warn("[ZLM 回调] on_server_keepalive 缺少 mediaServerId: {}", body);
            return HookResult.ok();
        }
        // 节点首次触达入负载索引（addIfAbsent，已存在不刷分数）
        loadIndex.onNodeOnline(serverId);
        // 与 MediaServerService.keepalive 等价：直接走 update 一次性更新避免 select-then-update
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

        // 节点首次触达入负载索引（即使节点在管理面已存在，也要确保入索引）
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
        // ZLM 重启：该节点下所有 INVITE 会话都已死；GbPlayService 的 onStreamStopped 会按需清扫
        // 推流表兜底同步：ZLM 上没有的 push 行删掉
        try {
            streamPushService.reconcileWithZlm(serverId);
        } catch (Exception e) {
            log.warn("[ZLM 回调] on_server_started 推流兜底同步异常 serverId={} err={}", serverId, e.getMessage());
        }
        // 拉流代理兜底：ZLM 重启后所有 addStreamProxy 创建的流都没了，对账后标 status=0 + lastError
        try {
            streamProxyService.reconcileWithZlm(serverId);
        } catch (Exception e) {
            log.warn("[ZLM 回调] on_server_started 拉流代理兜底异常 serverId={} err={}", serverId, e.getMessage());
        }
        return HookResult.ok();
    }

    // ============ 流级 ============

    public HookResult onPlay(Map<String, Object> body) {
        String id = stringOf(body, "id");
        String app = stringOf(body, "app");
        String stream = stringOf(body, "stream");
        log.info("[ZLM 回调] on_play id={} app={} stream={}", id, app, stream);
        try {
            // 记观看账，认不出人（没令牌/验签失败/ZLM 内部读者）就跳过，播放一律放行
            viewRecordService.openRecord(buildViewRecord(body, id, app, stream));
        } catch (Exception e) {
            log.warn("[ZLM 回调] on_play 观看记录落库失败 stream={} err={}", stream, e.getMessage());
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

    /** 按 wt 令牌认人，拼一条观看中记录；没有有效令牌返 null */
    private GbViewRecord buildViewRecord(Map<String, Object> body, String id, String app, String stream) {
        if (id == null || stream == null) {
            return null;
        }
        String wt = parseParam(stringOf(body, "params"), ViewTokenSigner.PARAM);
        ViewTokenSigner.ViewToken token = viewTokenSigner.verify(wt, stream);
        if (token == null) {
            log.info("[ZLM 回调] on_play 无有效观看令牌，跳过记录 stream={}", stream);
            return null;
        }
        GbViewRecord record = new GbViewRecord();
        record.setSessionId(id);
        record.setActorType(token.actorType());
        record.setActorId(token.actorId());
        record.setActorName(token.actorName());
        record.setStreamId(stream);
        record.setApp(app);
        record.setStreamSchema(stringOf(body, "schema"));
        record.setClientIp(stringOf(body, "ip"));
        record.setMediaServerId(stringOf(body, "mediaServerId"));
        record.setStartTime(LocalDateTime.now());
        // 设备/通道从点播会话补；查不到（外部推流的播放）就留空，流维度的统计不受影响
        GbInviteSession session = inviteSessionService.getByStreamId(stream);
        if (session != null) {
            record.setDeviceId(session.getDeviceId());
            record.setChannelId(session.getChannelId());
        }
        return record;
    }

    public HookResult onStreamNotFound(Map<String, Object> body) {
        String app = stringOf(body, "app");
        String stream = stringOf(body, "stream");
        String mediaServerId = stringOf(body, "mediaServerId");
        log.info("[ZLM 回调] on_stream_not_found app={} stream={}", app, stream);

        // autoApplyPlay 开启时，流不存在自动发起点播
        if (mediaProperties.isAutoApplyPlay()) {
            // streamId 格式: 主码流 {deviceId}_{channelId}（与前端点播一致）；
            // 子码流为 {deviceId}_{channelId}_{sanitize(码流token)}，其后缀经过 sanitize 不可逆推回原始
            // 厂商 token，故按需拉流只对主码流生效；子码流靠前端显式 start（带 token）拉起，此处跳过。
            String[] parts = stream == null ? null : stream.split("_");
            if (parts != null && parts.length == 2) {
                String deviceId = parts[0];
                String channelId = parts[1];
                log.info("[ZLM 回调] on_stream_not_found 自动发起点播 deviceId={} channelId={}", deviceId, channelId);
                // 异步发起点播，不阻塞 ZLM 回调
                playService.autoStartPlay(deviceId, channelId, mediaServerId);
            } else if (parts != null && parts.length > 2) {
                log.info("[ZLM 回调] on_stream_not_found 子码流流名 stream={}，按需拉流跳过（需前端带 token 显式点播）", stream);
            }
        }
        // 默认：允许 pull，autoApplyPlay 已经异步处理，ZLM 等待推送即可
        return HookResult.ok();
    }

    public HookResult onPublish(Map<String, Object> body) {
        log.info("[ZLM 回调] on_publish {}", body);

        String app = stringOf(body, "app");
        String stream = stringOf(body, "stream");

        // ============ 推流鉴权 ============
        // 国标 rtp / 广播 / 对讲 是平台自己触发的推流，不需要 token；其他外部推流才校验。
        boolean isPlatformInternalApp = "rtp".equals(app) || "broadcast".equals(app) || "talk".equals(app);
        if (mediaProperties.isPushAuthEnabled() && !isPlatformInternalApp) {
            String token = parseParam(stringOf(body, "params"), "token");
            // 与 StreamPushClientServiceImpl.authenticate 等价：token + enabled=1 才放行
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
        // ZLM 对每个派生 schema 都会触发一次（ts/rtsp/rtmp/fmp4），业务侧只需处理一次：固定取 rtsp
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
            // ONVIF 流自然结束（无人观看主路径 / RTSP 源异常断开非正常路径）→ 清 Redis 记账。
            // 无人观看主路径下 ZLM 已经内部 delStreamProxy；非正常路径下代理早就没了，都不需要
            // 再调 delStreamProxy，只删 key 即可。
            if (onvifPlayService.isOnvifStream(app, stream)) {
                try {
                    onvifPlayService.cleanupByStreamId(stream);
                } catch (Exception e) {
                    log.warn("[ZLM 回调] ONVIF 钩子清扫失败 stream={} err={}", stream, e.getMessage());
                }
            }
        }

        try {
            // 按 streamId 反查 session.sessionType 路由到对应业务：
            //  - DOWNLOAD → 录像下载（GbDownloadService）走 MP4 录制 + MinIO 落库
            //  - RECORD   → 云端录像（GbCloudRecordService）走 startRecord/断流清扫
            //  - PLAYBACK → 历史回放（GbPlaybackService）走流就绪补 URL
            //  - PLAY     → 实时点播（GbPlayService）走流就绪补 URL
            //  - null     → 找不到 session（流已被清扫 / 外部推流），按实时点播兜底；
            //               业务 service 内部若再 get 不到会无副作用返回。
            //               switch 对 null 会 String.hashCode() 抛 NPE，所以必须先判空。
            String sessionType = sessionTypeOf(stream);
            if (Boolean.TRUE.equals(regist)) {
                if (sessionType == null) {
                    // 级联喊话源流 / 对讲设备回传流都是 rtp/recv_*（不在点播会话里），归广播 service 管；其余外部推流按实时点播兜底
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
                // 推流入表：仅当 originType∈PUSH 时，StreamPushService 内部会再判一次
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
            // 找不到 session 时直接走实时点播兜底，避免 switch(null) 触发 String.hashCode() NPE
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
        if (session == null) {
            log.info("[ZLM 回调] on_record_mp4 无 session，跳过 stream={}", stream);
            return HookResult.ok();
        }
        // 按会话类型路由：下载走下载业务，云端录像走录像中心
        try {
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

    /**
     * TS 切片录像回调：平台只消费 MP4（on_record_mp4），这里仅应答成功，不落任何业务。
     * ZLM 侧若开启了 TS 录制会周期性回调，缺这个端点会刷 NoResourceFoundException。
     */
    public HookResult onRecordTs(Map<String, Object> body) {
        if (log.isDebugEnabled()) {
            log.debug("[ZLM 回调] on_record_ts 忽略 stream={}", body == null ? null : body.get("stream"));
        }
        return HookResult.ok();
    }

    public HookResult onSendRtpStopped(Map<String, Object> body) {
        log.info("[ZLM 回调] on_send_rtp_stopped {}", body);
        // 级联推流被 ZLM 中止（对端断开 / 超时）：按 stream + ssrc 清理级联推流会话（S6.b-3）
        try {
            String stream = stringOf(body, "stream");
            String ssrc = stringOf(body, "ssrc");
            if (stream != null) {
                cascadeSendService.onSendRtpStopped(stream, ssrc);
                // 语音广播的 startSendRtp 被 ZLM 中止也走这里，按 srcStream + ssrc 清扫广播会话
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

    /**
     * 推流鉴权：与 {@code StreamPushClientServiceImpl.authenticate} 等价，
     * 但本类直接走 {@link StreamPushClientMapper}，避免 service-in-service。
     * token 命中且 {@code enabled=1} 返回 clientId，其他情况返回 null。
     */
    private String authenticate(String token) {
        if (token == null || token.isBlank()) return null;
        StreamPushClient hit = streamPushClientMapper.selectOne(
                new LambdaQueryWrapper<StreamPushClient>()
                        .eq(StreamPushClient::getToken, token)
                        .eq(StreamPushClient::getEnabled, 1));
        return hit == null ? null : hit.getClientId();
    }

    // ============ 工具 ============

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

    /** 同 longOf，缺字段时返 null，落库时区分"没上报"和"上报 0" */
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

    /**
     * 解析 ZLM 上报的 {@code params} 串（形如 {@code token=abc&other=1}）取指定 key 的值。
     * <p>ZLM 在上报时会把 URL 里的 {@code =} / {@code &} 做 HTML 实体转义（{@code &#61;} / {@code &#38;}），
     * 因此拆分前先跑一遍简单的 HTML 实体解码。</p>
     */
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

    /**
     * 极简 HTML 实体解码：只处理 ZLM params 里可能出现的 {@code &amp; &#38; &#61; &quot;}。
     * 不引第三方库，覆盖数字实体（{@code &#N;} / {@code &#xNN;}）+ 常见命名实体。
     */
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
