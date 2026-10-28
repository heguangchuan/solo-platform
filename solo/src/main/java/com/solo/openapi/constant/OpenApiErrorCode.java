package com.solo.openapi.constant;

import lombok.Getter;

/**
 * 开放 API 错误码。
 * <p>分段：401xx 鉴权 / 403xx 授权 / 400xx 参数 / 429xx 限流 / 500xx 平台 / 60xxx 国标业务。
 * HTTP 状态码与业务码同时有意义，便于第三方网关层直接判断。
 */
@Getter
public enum OpenApiErrorCode {

    SUCCESS(200, 200, "success"),

    // 鉴权类 401
    MISSING_CREDENTIAL(401, 40101, "缺少鉴权信息或应用标识"),
    APP_NOT_FOUND(401, 40102, "应用不存在"),
    SIGNATURE_INVALID(401, 40103, "签名校验失败"),
    TIMESTAMP_EXPIRED(401, 40104, "请求时间戳过期"),
    NONCE_DUPLICATED(401, 40105, "重复请求"),

    // 授权类 403
    APP_DISABLED(403, 40301, "应用已停用"),
    APP_EXPIRED(403, 40302, "应用已过期"),
    SCOPE_DENIED(403, 40303, "应用未授权该接口"),
    IP_DENIED(403, 40304, "来源IP不在白名单"),

    // 参数类 400
    PARAM_INVALID(400, 40001, "请求参数不合法"),
    RESOURCE_NOT_FOUND(400, 40002, "资源不存在"),
    DEVICE_NOT_FOUND(400, 40003, "设备不存在"),
    DEVICE_OFFLINE(400, 40004, "设备不在线"),

    // 限流 429
    RATE_LIMITED(429, 42901, "请求过于频繁，触发限流"),
    DAILY_QUOTA_EXCEEDED(429, 42902, "当日调用量已超限"),

    // 平台类 500
    INTERNAL_ERROR(500, 50001, "平台内部错误"),

    // 国标业务类 60xxx
    SIP_INVITE_FAILED(200, 60001, "点播失败，设备未响应或拒绝"),
    STREAM_NOT_ACTIVE(200, 60002, "流不存在或已结束"),
    PLAYBACK_FAILED(200, 60003, "回放失败，设备未响应或拒绝");

    /** 对应 HTTP 状态码 */
    private final int httpStatus;

    /** 业务码 */
    private final int code;

    private final String message;

    OpenApiErrorCode(int httpStatus, int code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
