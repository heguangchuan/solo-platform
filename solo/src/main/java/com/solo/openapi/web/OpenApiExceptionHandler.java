package com.solo.openapi.web;

import com.solo.common.ServiceException;
import com.solo.openapi.constant.OpenApiConstants;
import com.solo.openapi.constant.OpenApiErrorCode;
import com.solo.openapi.exception.OpenApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 开放 API 专用异常处理，只作用于 /open-api/**，与内部 GlobalExceptionHandler 互不干扰。
 *
 * <p>必须优先于无包范围的 GlobalExceptionHandler：否则 OpenApiException / ServiceException
 * 会被全局兜底捕获，第三方将收到内部 Result 包装体（甚至 HTTP 200 + 系统异常），
 * 拿不到约定的 OpenApiResult 与开放错误码。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.solo.openapi.controller")
public class OpenApiExceptionHandler {

    @ExceptionHandler(OpenApiException.class)
    public ResponseEntity<OpenApiResult<Void>> handleOpenApi(OpenApiException e, HttpServletRequest request) {
        OpenApiErrorCode errorCode = e.getErrorCode();
        log.warn("[开放API] 业务异常 appKey={} code={} msg={}",
                request.getHeader(OpenApiConstants.HEADER_APP_KEY), errorCode.getCode(), e.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(OpenApiResult.error(errorCode.getCode(), e.getMessage(), requestId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<OpenApiResult<Void>> handleValid(MethodArgumentNotValidException e,
                                                           HttpServletRequest request) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .orElse(OpenApiErrorCode.PARAM_INVALID.getMessage());
        return ResponseEntity.status(OpenApiErrorCode.PARAM_INVALID.getHttpStatus())
                .body(OpenApiResult.error(OpenApiErrorCode.PARAM_INVALID.getCode(), msg, requestId(request)));
    }

    /**
     * 内部业务层统一抛 ServiceException，开放侧不能把这些原样回成 500。
     * 这里按消息关键字粗分到开放错误码，消息原文透传给对接方方便排查。
     */
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<OpenApiResult<Void>> handleService(ServiceException e, HttpServletRequest request) {
        OpenApiErrorCode errorCode = mapServiceError(e.getMessage());
        log.warn("[开放API] 业务异常 appKey={} code={} msg={}",
                request.getHeader(OpenApiConstants.HEADER_APP_KEY), errorCode.getCode(), e.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(OpenApiResult.error(errorCode.getCode(), e.getMessage(), requestId(request)));
    }

    private OpenApiErrorCode mapServiceError(String message) {
        if (message == null) {
            return OpenApiErrorCode.INTERNAL_ERROR;
        }
        if (message.contains("设备不存在") || message.contains("通道不存在")) {
            return OpenApiErrorCode.DEVICE_NOT_FOUND;
        }
        if (message.contains("离线")) {
            return OpenApiErrorCode.DEVICE_OFFLINE;
        }
        if (message.contains("不能为空")) {
            return OpenApiErrorCode.PARAM_INVALID;
        }
        if (message.contains("INVITE") || message.contains("点播超时") || message.contains("点播失败")
                || message.contains("正在请求点播") || message.contains("RTP 收流端口")
                || message.contains("SIP Provider")) {
            return OpenApiErrorCode.SIP_INVITE_FAILED;
        }
        return OpenApiErrorCode.INTERNAL_ERROR;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<OpenApiResult<Void>> handleOther(Exception e, HttpServletRequest request) {
        log.error("[开放API] 系统异常 requestId={}", requestId(request), e);
        return ResponseEntity.status(OpenApiErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(OpenApiResult.error(OpenApiErrorCode.INTERNAL_ERROR.getCode(),
                        OpenApiErrorCode.INTERNAL_ERROR.getMessage(), requestId(request)));
    }

    private String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(OpenApiConstants.HEADER_REQUEST_ID);
        return id != null ? id.toString() : request.getHeader(OpenApiConstants.HEADER_REQUEST_ID);
    }
}
