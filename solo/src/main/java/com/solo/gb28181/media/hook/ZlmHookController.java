package com.solo.gb28181.media.hook;

import com.solo.gb28181.config.MediaProperties;
import com.solo.gb28181.entity.GbMediaServer;
import com.solo.gb28181.service.GbMediaServerService;
import com.solo.gb28181.service.GbPlayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * ZLMediaKit Hook 回调接口。
 *
 * <h3>路径约定</h3>
 * <p>所有 hook 路径形如 {@code /api/gb28181/hook/zlm/<event>}，
 * 配合 ZLM {@code config.ini} 的 hook 段：
 * <pre>
 *   on_publish=http://&lt;solo&gt;:10003/api/gb28181/hook/zlm/on_publish
 *   on_stream_changed=http://&lt;solo&gt;:10003/api/gb28181/hook/zlm/on_stream_changed
 *   ...
 * </pre>
 *
 * <h3>响应约定</h3>
 * <p>ZLM 视 {@code {"code":0}} 为成功；任何 hook 异常仍需返回 code=0，否则 ZLM 会
 * 按 retry 重试，无谓地拍我们的接口。具体业务侧错误用日志承担，不用 HTTP 状态码反馈。</p>
 *
 * <h3>鉴权</h3>
 * <p>ZLM 默认 hook 请求不会带 secret，所以这里采用「若请求中存在 secret 字段则校验，
 * 不一致拒收；否则放行」的宽松策略；生产环境建议配合防火墙限制来源 IP。</p>
 *
 * <h3>S2.1 范围</h3>
 * <p>本次只实现 {@code on_server_keepalive / on_server_started / on_publish /
 * on_stream_changed / on_stream_none_reader / on_rtp_server_timeout} 六个，
 * 其中 {@code on_publish/on_stream_changed/on_stream_none_reader/on_rtp_server_timeout}
 * 暂以"返回 code=0 + 日志"占位，业务联动在 S2.2 接入 PlayService 后补齐。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/hook")
@RequiredArgsConstructor
public class ZlmHookController {

    private final MediaProperties mediaProperties;
    private final GbMediaServerService mediaServerService;
    private final GbPlayService playService;

    // ========== 服务级 hook ==========

    /**
     * ZLM 节点心跳，约定 10 秒一次
     */
    @PostMapping("/on_server_keepalive")
    public Map<String, Object> onServerKeepalive(@RequestBody(required = false) Map<String, Object> body) {
        if (!checkSecret(body)) {
            return reject();
        }
        String serverId = stringOf(body, "mediaServerId");
        if (serverId != null) {
            mediaServerService.keepalive(serverId);
        } else {
            log.warn("[ZLM 回调] on_server_keepalive 缺少 mediaServerId: {}", body);
        }
        return ok();
    }

    /**
     * ZLM 自身启动；启动后所有未收尾的会话都已死，需要清扫
     */
    @PostMapping("/on_server_started")
    public Map<String, Object> onServerStarted(@RequestBody(required = false) Map<String, Object> body) {
        if (!checkSecret(body)) {
            return reject();
        }
        String serverId = stringOf(body, "mediaServerId");
        log.info("[ZLM 回调] on_server_started serverId={}", serverId);
        if (serverId != null) {
            // 节点先 upsert：把 ZLM 自报的 mediaServerId 入库
            GbMediaServer exist = mediaServerService.getByServerId(serverId);
            if (exist == null) {
                log.warn("[ZLM 回调] on_server_started 但节点未在 gb_media_server 登记 serverId={}（请在管理面新增）", serverId);
            } else {
                exist.setStatus(1);
                exist.setHookAliveTime(LocalDateTime.now());
                mediaServerService.updateById(exist);
            }
            // TODO(S2.2): 把数据库中该节点下 INVITING/READY 的 invite_session 全部置为 STOPPED+fail_reason=zlm-restart
        }
        return ok();
    }

    // ========== 流级 hook（S2.2 接入业务） ==========

    /**
     * 流上来了（设备开始推 RTP，ZLM 已识别成功）
     */
    @PostMapping("/on_publish")
    public Map<String, Object> onPublish(@RequestBody(required = false) Map<String, Object> body) {
        if (!checkSecret(body)) {
            return reject();
        }
        log.info("[ZLM 回调] on_publish {}", body);
        // 默认允许推流、开启音频、不录 MP4（录像由我们 API 控制）
        Map<String, Object> resp = ok();
        resp.put("enable_audio", true);
        resp.put("enable_mp4", false);
        return resp;
    }

    /**
     * 流状态变化（注册/注销）
     */
    @PostMapping("/on_stream_changed")
    public Map<String, Object> onStreamChanged(@RequestBody(required = false) Map<String, Object> body) {
        if (!checkSecret(body)) {
            return reject();
        }
        Boolean regist = booleanOf(body, "regist");
        String app = stringOf(body, "app");
        String stream = stringOf(body, "stream");
        String mediaServerId = stringOf(body, "mediaServerId");
        log.info("[ZLM 回调] on_stream_changed app={} stream={} regist={}", app, stream, regist);
        try {
            if (Boolean.TRUE.equals(regist)) {
                playService.onStreamReady(app, stream, mediaServerId);
            } else {
                playService.onStreamStopped(app, stream, mediaServerId);
            }
        } catch (Exception e) {
            log.error("[ZLM 回调] on_stream_changed 业务处理异常 stream={}", stream, e);
        }
        return ok();
    }

    /**
     * 无人观看
     */
    @PostMapping("/on_stream_none_reader")
    public Map<String, Object> onStreamNoneReader(@RequestBody(required = false) Map<String, Object> body) {
        if (!checkSecret(body)) {
            return reject();
        }
        String stream = stringOf(body, "stream");
        log.info("[ZLM 回调] on_stream_none_reader stream={} -> close=true", stream);
        Map<String, Object> resp = ok();
        resp.put("close", true);
        return resp;
    }

    /**
     * 申请的 RTP 端口长时间没收到数据（一般是设备没推、或防火墙挡住）
     */
    @PostMapping("/on_rtp_server_timeout")
    public Map<String, Object> onRtpServerTimeout(@RequestBody(required = false) Map<String, Object> body) {
        if (!checkSecret(body)) {
            return reject();
        }
        String streamId = stringOf(body, "stream_id");
        log.warn("[ZLM 回调] on_rtp_server_timeout stream_id={}", streamId);
        try {
            playService.onRtpServerTimeout(streamId);
        } catch (Exception e) {
            log.error("[ZLM 回调] on_rtp_server_timeout 业务处理异常 stream_id={}", streamId, e);
        }
        return ok();
    }

    /**
     * 录像 MP4 完成；S4 录像功能里再处理
     */
    @PostMapping("/on_record_mp4")
    public Map<String, Object> onRecordMp4(@RequestBody(required = false) Map<String, Object> body) {
        if (!checkSecret(body)) {
            return reject();
        }
        log.info("[ZLM 回调] on_record_mp4 {}", body);
        return ok();
    }

    /**
     * 国标级联向上推流停止；S6 用
     */
    @PostMapping("/on_send_rtp_stopped")
    public Map<String, Object> onSendRtpStopped(@RequestBody(required = false) Map<String, Object> body) {
        if (!checkSecret(body)) {
            return reject();
        }
        log.info("[ZLM 回调] on_send_rtp_stopped {}", body);
        return ok();
    }

    // ========== 工具 ==========

    private boolean checkSecret(Map<String, Object> body) {
        if (body == null) {
            return true;
        }
        String expected = mediaProperties.getHookSecret();
        if (expected == null || expected.isBlank()) {
            return true;
        }
        Object got = body.get("secret");
        if (got == null) {
            // ZLM 默认 hook 不带 secret，宽松通过；如需严格，可改为 return false;
            return true;
        }
        if (!expected.equals(got.toString())) {
            log.warn("[ZLM 回调] secret 不匹配 from={}", got);
            return false;
        }
        return true;
    }

    private static Map<String, Object> ok() {
        Map<String, Object> m = new HashMap<>();
        m.put("code", 0);
        m.put("msg", "success");
        return m;
    }

    private static Map<String, Object> reject() {
        Map<String, Object> m = new HashMap<>();
        m.put("code", 401);
        m.put("msg", "secret mismatch");
        return m;
    }

    private static String stringOf(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v == null ? null : v.toString();
    }

    private static Boolean booleanOf(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(v.toString());
    }
}
