package com.solo.media.hook;

import java.util.LinkedHashMap;
import java.util.Map;

public class HookResult {

    private final Map<String, Object> body = new LinkedHashMap<>();

    private HookResult(int code, String msg) {
        body.put("code", code);
        body.put("msg", msg);
    }

    public static HookResult ok() {
        return new HookResult(0, "success");
    }

    // secret 不对时 Controller 会转成 HTTP 401，让 ZLM 别一直重试
    public static HookResult reject(String msg) {
        return new HookResult(401, msg);
    }

    // on_publish 要保持 HTTP 200，再靠 code=-1 断开推流，不然 ZLM 会重试
    public static HookResult rejectPublish(String msg) {
        return new HookResult(-1, msg);
    }

    // on_play 也是 HTTP 200 + 非零 code 才拒绝播放
    public static HookResult rejectPlay(String msg) {
        return new HookResult(-1, msg);
    }

    // on_stream_not_found 偏偏反过来：得回 code=0 + close=true。
    // 回 code=-1 时 ZLM 只看 hook 失败，根本不读 close。
    public static HookResult closeNotFoundStream(String msg) {
        return new HookResult(0, msg).put("close", true);
    }

    public HookResult put(String key, Object value) {
        body.put(key, value);
        return this;
    }

    public Map<String, Object> toMap() {
        return body;
    }
}
