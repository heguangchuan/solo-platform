package com.solo.media.hook;

import com.solo.media.properties.MediaProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * ZLMediaKit Hook 入口
 *
 * <p>路径形如 {@code /api/hook/on_xxx}，对应 ZLM {@code config.ini} 的 hook 段。</p>
 * <p>本类做两件事：<b>secret 强制校验</b> + <b>URL 绑定后转发到 {@link ZlmHookService}</b>。
 * 全部业务编排在 Service 中；secret 校验失败直接返 HTTP 401，让 ZLM 停止 retry。</p>
 *
 * <h3>Secret 校验策略</h3>
 * <p>{@code gb28181.media.hook-secret} <b>必须配置</b>（生产 / 测试 / 内网一视同仁），
 * 未配置时所有 hook 一律拒绝。ZLM 侧需在 {@code [hook].on_xxx} URL 上带 {@code ?secret=xxx}
 * 或让 body 携带 secret 字段。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/hook")
@RequiredArgsConstructor
public class ZlmHookController {

    private final ZlmHookService hookService;
    private final MediaProperties mediaProperties;

    @PostMapping("/on_server_keepalive")
    public Map<String, Object> onServerKeepalive(HttpServletRequest req, @RequestBody(required = false) Map<String, Object> body) {
        if (!checkSecret(req, body)) return unauthorized();
        return hookService.onServerKeepalive(body).toMap();
    }

    @PostMapping("/on_server_started")
    public Map<String, Object> onServerStarted(HttpServletRequest req, @RequestBody(required = false) Map<String, Object> body) {
        if (!checkSecret(req, body)) return unauthorized();
        return hookService.onServerStarted(body).toMap();
    }

    @PostMapping("/on_play")
    public Map<String, Object> onPlay(HttpServletRequest req, @RequestBody(required = false) Map<String, Object> body) {
        if (!checkSecret(req, body)) return unauthorized();
        return hookService.onPlay(body).toMap();
    }

    @PostMapping("/on_stream_not_found")
    public Map<String, Object> onStreamNotFound(HttpServletRequest req, @RequestBody(required = false) Map<String, Object> body) {
        if (!checkSecret(req, body)) return unauthorized();
        return hookService.onStreamNotFound(body).toMap();
    }

    @PostMapping("/on_publish")
    public Map<String, Object> onPublish(HttpServletRequest req, @RequestBody(required = false) Map<String, Object> body) {
        if (!checkSecret(req, body)) return unauthorized();
        return hookService.onPublish(body).toMap();
    }

    @PostMapping("/on_stream_changed")
    public Map<String, Object> onStreamChanged(HttpServletRequest req, @RequestBody(required = false) Map<String, Object> body) {
        if (!checkSecret(req, body)) return unauthorized();
        return hookService.onStreamChanged(body).toMap();
    }

    @PostMapping("/on_stream_none_reader")
    public Map<String, Object> onStreamNoneReader(HttpServletRequest req, @RequestBody(required = false) Map<String, Object> body) {
        if (!checkSecret(req, body)) return unauthorized();
        return hookService.onStreamNoneReader(body).toMap();
    }

    @PostMapping("/on_rtp_server_timeout")
    public Map<String, Object> onRtpServerTimeout(HttpServletRequest req, @RequestBody(required = false) Map<String, Object> body) {
        if (!checkSecret(req, body)) return unauthorized();
        return hookService.onRtpServerTimeout(body).toMap();
    }

    @PostMapping("/on_record_mp4")
    public Map<String, Object> onRecordMp4(HttpServletRequest req, @RequestBody(required = false) Map<String, Object> body) {
        if (!checkSecret(req, body)) return unauthorized();
        return hookService.onRecordMp4(body).toMap();
    }

    @PostMapping("/on_send_rtp_stopped")
    public Map<String, Object> onSendRtpStopped(HttpServletRequest req, @RequestBody(required = false) Map<String, Object> body) {
        if (!checkSecret(req, body)) return unauthorized();
        return hookService.onSendRtpStopped(body).toMap();
    }

    // ============================================================
    //  Secret 校验
    // ============================================================

    /**
     * 校验 hook 请求携带的 secret。返回 true 表示通过、false 表示拒绝。
     * <p>{@code gb28181.media.hook-secret} <b>必须配置</b>，未配置一律拒绝；
     * secret 必须命中 body.secret 或 URL query 参数 secret 之一。</p>
     */
    private boolean checkSecret(HttpServletRequest req, Map<String, Object> body) {
        String expected = mediaProperties.getHookSecret();
        if (expected == null || expected.isBlank()) {
            log.error("[ZLM 回调] 拒绝：gb28181.media.hook-secret 未配置，请在 application.yaml 补齐 uri={}",
                    req.getRequestURI());
            return false;
        }
        String got = extractSecret(req, body);
        if (got == null) {
            log.warn("[ZLM 回调] 拒绝：缺少 secret uri={} remoteIp={}",
                    req.getRequestURI(), req.getRemoteAddr());
            return false;
        }
        if (!constantTimeEquals(expected, got)) {
            log.warn("[ZLM 回调] 拒绝：secret 不匹配 uri={} remoteIp={}",
                    req.getRequestURI(), req.getRemoteAddr());
            return false;
        }
        return true;
    }

    /** 从 body 或 URL query 中取 secret（ZLM 两种发送方式都支持） */
    private static String extractSecret(HttpServletRequest req, Map<String, Object> body) {
        if (body != null) {
            Object v = body.get("secret");
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        String q = req.getParameter("secret");
        return q == null || q.isBlank() ? null : q;
    }

    /** 常量时间字符串比较，避免定时侧信道（对 hook 影响甚微，顺手做） */
    private static boolean constantTimeEquals(String a, String b) {
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(ab, bb);
    }

    private static Map<String, Object> unauthorized() {
        Map<String, Object> m = new HashMap<>();
        m.put("code", 401);
        m.put("msg", "secret invalid");
        return m;
    }
}
