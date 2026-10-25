package com.solo.openapi.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.solo.openapi.constant.OpenApiErrorCode;
import lombok.Data;

/**
 * 开放 API 统一响应体。比内部 {@code Result} 多 requestId，方便第三方报障对账。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenApiResult<T> {

    private int code;
    private String message;
    private T data;
    private String requestId;

    public static <T> OpenApiResult<T> success(T data, String requestId) {
        OpenApiResult<T> r = new OpenApiResult<>();
        r.code = OpenApiErrorCode.SUCCESS.getCode();
        r.message = "success";
        r.data = data;
        r.requestId = requestId;
        return r;
    }

    public static OpenApiResult<Void> error(int code, String message, String requestId) {
        OpenApiResult<Void> r = new OpenApiResult<>();
        r.code = code;
        r.message = message;
        r.requestId = requestId;
        return r;
    }
}
