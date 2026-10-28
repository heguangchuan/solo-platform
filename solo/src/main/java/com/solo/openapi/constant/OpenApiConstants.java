package com.solo.openapi.constant;

/**
 * 开放 API 常量：请求头、Redis key、签名相关。
 */
public interface OpenApiConstants {

    /** 开放 API 路径前缀，JWT 拦截器对此放行 */
    String PATH_PREFIX = "/open-api/";

    // ===== 签名请求头 =====
    String HEADER_APP_KEY = "X-App-Key";
    String HEADER_TIMESTAMP = "X-Timestamp";
    String HEADER_NONCE = "X-Nonce";
    String HEADER_SIGN_ALG = "X-Sign-Alg";
    String HEADER_SIGNATURE = "X-Signature";
    String HEADER_REQUEST_ID = "X-Request-Id";

    /** 默认签名算法 */
    String SIGN_ALG_HMAC_SHA256 = "HMAC-SHA256";

    /** 时间戳允许偏差（秒） */
    long TIMESTAMP_TOLERANCE_SECONDS = 300;

    /** nonce 防重放留存（秒），略大于时间窗口 */
    long NONCE_TTL_SECONDS = 600;

    // ===== Redis key，统一 openapi 前缀 =====
    /** nonce 去重：openapi:nonce:{appKey}:{nonce} */
    String NONCE_PREFIX = "openapi:nonce:";

    /** 应用档案缓存（含密钥明文，仅服务端使用）：openapi:app:{appKey} */
    String APP_CACHE_PREFIX = "openapi:app:";

    /** 应用档案缓存 TTL（分钟） */
    long APP_CACHE_TTL_MINUTES = 10;

    /** QPS 滑动窗口计数：openapi:rl:qps:{appKey}:{秒} */
    String RATE_QPS_PREFIX = "openapi:rl:qps:";

    /** 日调用量计数：openapi:rl:day:{appKey}:{yyyyMMdd} */
    String RATE_DAY_PREFIX = "openapi:rl:day:";

    // ===== Scope 授权范围 =====
    String SCOPE_DEVICE_READ = "gb:device:read";
    String SCOPE_DEVICE_WRITE = "gb:device:write";
    String SCOPE_PLAY_WRITE = "gb:play:write";
    String SCOPE_PLAYBACK_CTRL = "gb:playback:ctrl";
    String SCOPE_RECORD_READ = "gb:record:read";
    String SCOPE_DOWNLOAD_CTRL = "gb:download:ctrl";
    String SCOPE_PTZ_CTRL = "gb:ptz:ctrl";
    String SCOPE_CONTROL_CTRL = "gb:control:ctrl";
    String SCOPE_ALARM_READ = "gb:alarm:read";
    String SCOPE_POSITION_READ = "gb:position:read";
    String SCOPE_EVENT_SUBSCRIBE = "gb:event:subscribe";

    // ===== Webhook 事件类型 =====
    String EVENT_DEVICE_ONLINE = "device.online";
    String EVENT_DEVICE_OFFLINE = "device.offline";
    String EVENT_DEVICE_ALARM = "device.alarm";

    /** 请求上下文里的应用属性名 */
    String ATTR_APP_CONTEXT = "openApiAppContext";
}
