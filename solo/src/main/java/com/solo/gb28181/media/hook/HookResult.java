package com.solo.gb28181.media.hook;

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

    public static HookResult reject(String msg) {
        return new HookResult(401, msg);
    }

    public HookResult put(String key, Object value) {
        body.put(key, value);
        return this;
    }

    public Map<String, Object> toMap() {
        return body;
    }
}
