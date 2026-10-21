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

    private boolean checkSecret(HttpServletRequest req, Map<String, Object> body) {
        String expected = mediaProperties.getHookSecret();
        if (expected == null || expected.isBlank()) {
            log.error("[ZLM 回调] 拒绝：media.hook-secret 未配置，请在 application.yaml 补齐 uri={}",
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

    private static String extractSecret(HttpServletRequest req, Map<String, Object> body) {
        if (body != null) {
            Object v = body.get("secret");
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        String q = req.getParameter("secret");
        return q == null || q.isBlank() ? null : q;
    }
    
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
