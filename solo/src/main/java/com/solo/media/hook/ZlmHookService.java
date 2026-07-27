package com.solo.media.hook;

import com.solo.media.properties.MediaProperties;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.media.entity.MediaServer;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.media.service.StreamPushClientService;
import com.solo.media.service.StreamPushService;
import com.solo.media.service.MediaServerService;
import com.solo.gb28181.service.GbPlayService;
import com.solo.gb28181.service.GbPlaybackService;
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
 *   <li>转发到对应业务服务（{@link MediaServerService} / {@link GbPlayService}）</li>
 * </ul>
 *
 * <p>所有方法接收 ZLM 原始 body，返回 {@link HookResult}。Secret 校验由 {@link ZlmHookController}
 * 统一负责，业务方法内无需再判。</p>
 *
 * <h3>响应约定</h3>
 * <p>业务异常一律用日志承担并返 {@code code=0}，避免 ZLM 因 4xx/5xx 反复重试。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZlmHookService {

    private final MediaProperties mediaProperties;
    private final MediaServerService mediaServerService;
    private final GbInviteSessionService inviteSessionService;
    private final GbPlayService playService;
    private final GbPlaybackService playbackService;
    private final StreamPushService streamPushService;
    private final StreamPushClientService streamPushClientService;

    // ============ 服务级 ============

    public HookResult onServerKeepalive(Map<String, Object> body) {
        String serverId = stringOf(body, "mediaServerId");
        if (serverId == null) {
            log.warn("[ZLM 回调] on_server_keepalive 缺少 mediaServerId: {}", body);
            return HookResult.ok();
        }
        mediaServerService.keepalive(serverId);
        return HookResult.ok();
    }

    public HookResult onServerStarted(Map<String, Object> body) {
        String serverId = stringOf(body, "mediaServerId");
        log.info("[ZLM 回调] on_server_started serverId={}", serverId);
        if (serverId == null) return HookResult.ok();

        MediaServer exist = mediaServerService.lambdaQuery()
                .eq(MediaServer::getServerId, serverId).one();
        if (exist == null) {
            log.warn("[ZLM 回调] on_server_started 但节点未在 gb_media_server 登记 serverId={}（请在管理面新增）", serverId);
        } else {
            exist.setStatus(1);
            exist.setHookAliveTime(LocalDateTime.now());
            mediaServerService.updateById(exist);
        }
        // ZLM 重启：该节点下所有 INVITE 会话都已死；GbPlayService 的 onStreamStopped 会按需清扫
        // 推流表兜底同步：ZLM 上没有的 push 行删掉
        try {
            streamPushService.reconcileWithZlm(serverId);
        } catch (Exception e) {
            log.warn("[ZLM 回调] on_server_started 推流兜底同步异常 serverId={} err={}", serverId, e.getMessage());
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
            // streamId 格式: {deviceId}_{channelId} 和前端点播一致
            String[] parts = null;
            if (stream != null) {
                parts = stream.split("_", 2);
            }
            if (parts != null && parts.length == 2) {
                String deviceId = parts[0];
                String channelId = parts[1];
                log.info("[ZLM 回调] on_stream_not_found 自动发起点播 deviceId={} channelId={}", deviceId, channelId);
                // 异步发起点播，不阻塞 ZLM 回调
                playService.autoStartPlay(deviceId, channelId, mediaServerId);
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
            String clientId = streamPushClientService.authenticate(token);
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
        try {
            // 按 streamId 反查 session 判断是点播还是回放 — 两个 service 都收到 hook 但只处理自己的会话
            boolean isPlayback = isPlaybackStream(stream);
            if (Boolean.TRUE.equals(regist)) {
                if (isPlayback) {
                    playbackService.onStreamReady(app, stream, mediaServerId);
                } else {
                    playService.onStreamReady(app, stream, mediaServerId);
                }
                // 推流入表：仅当 originType∈PUSH 时，StreamPushService 内部会再判一次
                streamPushService.onStreamRegist(app, stream, mediaServerId, body);
            } else {
                if (isPlayback) {
                    playbackService.onStreamStopped(app, stream, mediaServerId);
                } else {
                    playService.onStreamStopped(app, stream, mediaServerId);
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
        String mediaServerId = stringOf(body, "mediaServerId");
        String originTypeStr = stringOf(body, "originTypeStr");
        // close=false：前端 WS-FLV 与 ZLM 收到 RTP 之间有几百 ms 延迟，立刻关流会导致前端拉不到。
        // 流的回收完全靠业务侧（stop API / on_rtp_server_timeout）
        boolean close = false;

        // 只对"平台自己拉起来"的流（国标 INVITE 的 rtp_push、拉流代理的 pull）应用按需回收；
        // 推流类型（rtmp_push / rtsp_push / rtc_push）由客户端主动推，无人看 ≠ 该关，否则 ffmpeg 侧会 WSAECONNABORTED。
        boolean isPullOrInvite = "rtp_push".equals(originTypeStr)
                || "pull".equals(originTypeStr)
                || "ffmpeg_pull".equals(originTypeStr);

        // streamOnDemand 开启时，如果没有人看，告诉 ZLM 关闭流，释放资源（仅限拉起来的流）
        if (mediaProperties.isStreamOnDemand() && isPullOrInvite) {
            close = true;
            // 异步清理 GB28181 会话资源
            if ("rtsp".equalsIgnoreCase(stringOf(body, "schema"))) {
                GbInviteSession session = inviteSessionService.getByStreamId(stream);
                if (session != null) {
                    log.info("[按需拉流] on_stream_none_reader 关闭空闲流 stream={}", stream);
                    if (session.getSessionType().equals(GbInviteSession.TYPE_PLAYBACK)
                            || session.getSessionType().equals(GbInviteSession.TYPE_DOWNLOAD)) {
                        playbackService.onRtpServerTimeout(stream);
                    } else {
                        playService.onRtpServerTimeout(stream);
                    }
                }
            }
        }

        log.info("[ZLM 回调] on_stream_none_reader stream={} originType={} -> close={}",
                stream, originTypeStr, close);
        return HookResult.ok().put("close", close);
    }

    public HookResult onRtpServerTimeout(Map<String, Object> body) {
        String streamId = stringOf(body, "stream_id");
        log.warn("[ZLM 回调] on_rtp_server_timeout stream_id={}", streamId);
        try {
            if (isPlaybackStream(streamId)) {
                playbackService.onRtpServerTimeout(streamId);
            } else {
                playService.onRtpServerTimeout(streamId);
            }
        } catch (Exception e) {
            log.error("[ZLM 回调] on_rtp_server_timeout 业务异常 stream_id={}", streamId, e);
        }
        return HookResult.ok();
    }

    /**
     * 按 streamId 反查 session.sessionType，判断是否为回放流（PLAYBACK / DOWNLOAD 都视为非实时）。
     * 找不到 session 时按"非回放"处理：让 play service 兜底（回放 service 自己 get session 也会拿 null，无副作用）。
     */
    private boolean isPlaybackStream(String streamId) {
        if (streamId == null) return false;
        GbInviteSession s = inviteSessionService.getByStreamId(streamId);
        if (s == null) return false;
        return GbInviteSession.TYPE_PLAYBACK.equals(s.getSessionType())
                || GbInviteSession.TYPE_DOWNLOAD.equals(s.getSessionType());
    }

    public HookResult onRecordMp4(Map<String, Object> body) {
        log.info("[ZLM 回调] on_record_mp4 {}", body);
        // S4 录像功能再接入
        return HookResult.ok();
    }

    public HookResult onSendRtpStopped(Map<String, Object> body) {
        log.info("[ZLM 回调] on_send_rtp_stopped {}", body);
        // S6 级联用
        return HookResult.ok();
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
