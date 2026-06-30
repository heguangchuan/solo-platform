package com.solo.gb28181.media.hook;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ZLMediaKit Hook 入口
 *
 * <p>路径形如 {@code /api/hook/on_xxx}，对应 ZLM {@code config.ini} 的 hook 段。</p>
 * <p>全部业务编排在 {@link ZlmHookService}，本类只做 URL 绑定 + 转发。</p>
 *
 * <p>响应统一 {@link Map}：ZLM 视 {@code code=0} 为成功；任何业务异常 ZlmHookService 都吞下并
 * 返回 code=0，避免 ZLM 反复 retry。</p>
 */
@RestController
@RequestMapping("/api/hook")
@RequiredArgsConstructor
public class ZlmHookController {

    private final ZlmHookService hookService;

    @PostMapping("/on_server_keepalive")
    public Map<String, Object> onServerKeepalive(@RequestBody(required = false) Map<String, Object> body) {
        return hookService.onServerKeepalive(body).toMap();
    }

    @PostMapping("/on_server_started")
    public Map<String, Object> onServerStarted(@RequestBody(required = false) Map<String, Object> body) {
        return hookService.onServerStarted(body).toMap();
    }

    @PostMapping("/on_play")
    public Map<String, Object> onPlay(@RequestBody(required = false) Map<String, Object> body) {
        return hookService.onPlay(body).toMap();
    }

    @PostMapping("/on_stream_not_found")
    public Map<String, Object> onStreamNotFound(@RequestBody(required = false) Map<String, Object> body) {
        return hookService.onStreamNotFound(body).toMap();
    }

    @PostMapping("/on_publish")
    public Map<String, Object> onPublish(@RequestBody(required = false) Map<String, Object> body) {
        return hookService.onPublish(body).toMap();
    }

    @PostMapping("/on_stream_changed")
    public Map<String, Object> onStreamChanged(@RequestBody(required = false) Map<String, Object> body) {
        return hookService.onStreamChanged(body).toMap();
    }

    @PostMapping("/on_stream_none_reader")
    public Map<String, Object> onStreamNoneReader(@RequestBody(required = false) Map<String, Object> body) {
        return hookService.onStreamNoneReader(body).toMap();
    }

    @PostMapping("/on_rtp_server_timeout")
    public Map<String, Object> onRtpServerTimeout(@RequestBody(required = false) Map<String, Object> body) {
        return hookService.onRtpServerTimeout(body).toMap();
    }

    @PostMapping("/on_record_mp4")
    public Map<String, Object> onRecordMp4(@RequestBody(required = false) Map<String, Object> body) {
        return hookService.onRecordMp4(body).toMap();
    }

    @PostMapping("/on_send_rtp_stopped")
    public Map<String, Object> onSendRtpStopped(@RequestBody(required = false) Map<String, Object> body) {
        return hookService.onSendRtpStopped(body).toMap();
    }
}
