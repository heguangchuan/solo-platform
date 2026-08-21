package com.solo.media.hook;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.service.GbDownloadService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.service.GbPlayService;
import com.solo.gb28181.service.GbPlaybackService;
import com.solo.gb28181.service.impl.CascadeSendService;
import com.solo.media.entity.MediaServer;
import com.solo.media.entity.StreamPushClient;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.mapper.StreamPushClientMapper;
import com.solo.media.properties.MediaProperties;
import com.solo.media.service.MediaServerLoader;
import com.solo.media.service.StreamProxyService;
import com.solo.media.service.StreamPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * ZLM Hook 业务编排
 *
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
    private final StreamPushService streamPushService;
    private final StreamProxyService streamProxyService;
    private final CascadeSendService cascadeSendService;

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
        log.info("[ZLM 回调] on_play {}", body);
        // 实时点播默认放行；如需限制只许带正确 callId 的客户端拉流，可在此扩展
        return HookResult.ok();
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
        }

        try {
            // 按 streamId 反查 session.sessionType 路由到对应业务：
            //  - DOWNLOAD → 录像下载（GbDownloadService）走 MP4 录制 + MinIO 落库
            //  - PLAYBACK → 历史回放（GbPlaybackService）走流就绪补 URL
            //  - PLAY     → 实时点播（GbPlayService）走流就绪补 URL
            //  - null     → 找不到 session，按实时点播兜底（业务 service 内部再 get 一次也无副作用）
            String sessionType = sessionTypeOf(stream);
            if (Boolean.TRUE.equals(regist)) {
                switch (sessionType) {
                    case GbInviteSession.TYPE_DOWNLOAD -> downloadService.onStreamReady(app, stream, mediaServerId);
                    case GbInviteSession.TYPE_PLAYBACK -> playbackService.onStreamReady(app, stream, mediaServerId);
                    default -> playService.onStreamReady(app, stream, mediaServerId);
                }
                // 推流入表：仅当 originType∈PUSH 时，StreamPushService 内部会再判一次
                streamPushService.onStreamRegister(app, stream, mediaServerId, body);
            } else {
                switch (sessionType) {
                    case GbInviteSession.TYPE_DOWNLOAD -> downloadService.onStreamStopped(app, stream, mediaServerId);
                    case GbInviteSession.TYPE_PLAYBACK -> playbackService.onStreamStopped(app, stream, mediaServerId);
                    default -> playService.onStreamStopped(app, stream, mediaServerId);
                }
                streamPushService.onStreamCancel(app, stream, mediaServerId);
            }
        } catch (Exception e) {
            log.error("[ZLM 回调] on_stream_changed 业务异常 stream={}", stream, e);
        }
        return HookResult.ok();
    }

    public HookResult onStreamNoneReader(Map<String, Object> body) {
        String stream = stringOf(body, "stream");
        String originTypeStr = stringOf(body, "originTypeStr");
        // close=false：前端 WS-FLV 与 ZLM 收到 RTP 之间有几百 ms 延迟；刷新 / 切码流 / 网络抖动也会造成
        // 短暂无人观看的空窗，若此时立刻关流，用户重连要重新走一遍 INVITE。故这里不立即关，
        // 流的回收统一靠业务侧：stop API（用户主动关闭）+ on_rtp_server_timeout（RTP 端口长时间无数据兜底）。
        // 注：ZLM 的 on_stream_none_reader 回调不带 originTypeStr（实测为 null），故不以它做判据，仅记录。
        boolean close = false;
        boolean known = inviteSessionService.getByStreamId(stream) != null;

        log.info("[ZLM 回调] on_stream_none_reader stream={} originType={} 会话在册={} -> close={}",
                stream, originTypeStr, known, close);
        return HookResult.ok().put("close", close);
    }

    public HookResult onRtpServerTimeout(Map<String, Object> body) {
        String streamId = stringOf(body, "stream_id");
        log.warn("[ZLM 回调] on_rtp_server_timeout stream_id={}", streamId);
        try {
            String sessionType = sessionTypeOf(streamId);
            switch (sessionType) {
                case GbInviteSession.TYPE_DOWNLOAD -> downloadService.onRtpServerTimeout(streamId);
                case GbInviteSession.TYPE_PLAYBACK -> playbackService.onRtpServerTimeout(streamId);
                default -> playService.onRtpServerTimeout(streamId);
            }
        } catch (Exception e) {
            log.error("[ZLM 回调] on_rtp_server_timeout 业务异常 stream_id={}", streamId, e);
        }
        return HookResult.ok();
    }

    /**
     * 按 streamId 反查 session.sessionType。
     * <p>路由依据：</p>
     * <ul>
     *   <li>{@code "PLAY"} — 实时点播（GbPlayService）</li>
     *   <li>{@code "PLAYBACK"} — 历史回放（GbPlaybackService）</li>
     *   <li>{@code "DOWNLOAD"} — 录像下载（GbDownloadService）</li>
     *   <li>{@code null} — 找不到 session（流已被清扫 / 外部推流），按"实时点播"兜底；
     *       业务 service 内部若再 get 不到会无副作用返回</li>
     * </ul>
     */
    private String sessionTypeOf(String streamId) {
        if (streamId == null) return null;
        GbInviteSession s = inviteSessionService.getByStreamId(streamId);
        return s == null ? null : s.getSessionType();
    }

    public HookResult onRecordMp4(Map<String, Object> body) {
        String app = stringOf(body, "app");
        String stream = stringOf(body, "stream");
        String url = stringOf(body, "url");
        long fileSize = longOf(body, "file_size");
        double duration = doubleOf(body, "time_len");
        log.info("[ZLM 回调] on_record_mp4 app={} stream={} url={} fileSize={} duration={}s",
                app, stream, url, fileSize, duration);
        if (stream == null) {
            return HookResult.ok();
        }
        // 路由判定：只有 DOWNLOAD sessionType 才走下载业务；
        // 若该 stream 找不到 session（已被清理 / 兜底走 playback），记日志放过即可
        GbInviteSession session = inviteSessionService.getByStreamId(stream);
        if (session == null || !GbInviteSession.TYPE_DOWNLOAD.equals(session.getSessionType())) {
            log.info("[ZLM 回调] on_record_mp4 非下载流/无 session，跳过 stream={}", stream);
            return HookResult.ok();
        }
        try {
            downloadService.onRecordMp4(app, stream, url, fileSize, duration);
        } catch (Exception e) {
            log.error("[ZLM 回调] on_record_mp4 业务异常 stream={}", stream, e);
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

    /** 日志脱敏：token 只保留前 6 位 */
    private static String maskToken(String token) {
        if (token == null) return "null";
        if (token.length() < 6) return "***";
        return token.substring(0, 6) + "***";
    }
}
