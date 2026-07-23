package com.solo.media.hook;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ZLM Hook 统一响应体
 * <p>ZLM 视 {@code code=0} 为成功，业务侧错误一律用日志兜底，不通过 HTTP 状态码反馈
 * （否则 ZLM 会按 retry 反复请求）。</p>
 * <p>支持携带扩展字段（如 on_publish 的 {@code enable_audio}、on_stream_none_reader 的
 * {@code close}），通过 {@link #put(String, Object)} 链式追加即可。</p>
 */
public class HookResult {

    private final Map<String, Object> body = new LinkedHashMap<>();

    private HookResult(int code, String msg) {
        body.put("code", code);
        body.put("msg", msg);
    }

    public static HookResult ok() {
        return new HookResult(0, "success");
    }

    /** 用于 secret 校验失败：Controller 层会把 code=401 转为 HTTP 401，让 ZLM 停止 retry */
    public static HookResult reject(String msg) {
        return new HookResult(401, msg);
    }

    /**
     * 用于 on_publish 拒绝推流：HTTP 保持 200 让 ZLM 不 retry，
     * 但 code=-1 让 ZLM 立即断开推流方连接。ffmpeg 侧会收到 WSAECONNABORTED / EOF。
     */
    public static HookResult rejectPublish(String msg) {
        return new HookResult(-1, msg);
    }

    public HookResult put(String key, Object value) {
        body.put(key, value);
        return this;
    }

    public Map<String, Object> toMap() {
        return body;
    }
}
