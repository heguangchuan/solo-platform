package com.solo.openapi.exception;

import com.solo.openapi.constant.OpenApiErrorCode;
import lombok.Getter;

/**
 * 开放 API 业务异常。携带错误码枚举，由开放 API 专用异常处理器统一返回。
 */
@Getter
public class OpenApiException extends RuntimeException {

    private final OpenApiErrorCode errorCode;

    public OpenApiException(OpenApiErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public OpenApiException(OpenApiErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
