package com.solo.config;

import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.system.constant.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<Result<Void>> handleServiceException(ServiceException e) {
        log.warn("[全局异常] 业务异常 编码:{} 错误:{}", e.getCode(), e.getMessage());
        Result<Void> body = Result.error(e.getCode(), e.getMessage());
        return switch (e.getCode()) {
            case SystemConstants.HTTP_BAD_REQUEST,
                 SystemConstants.HTTP_UNAUTHORIZED,
                 SystemConstants.HTTP_FORBIDDEN,
                 SystemConstants.HTTP_NOT_FOUND,
                 SystemConstants.HTTP_TOO_MANY_REQUESTS,
                 SystemConstants.HTTP_INTERNAL_ERROR -> ResponseEntity.status(e.getCode()).body(body);
            default -> ResponseEntity.ok(body);
        };
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过限制: {}", e.getMessage());
        return ResponseEntity.status(SystemConstants.HTTP_BAD_REQUEST)
                .body(Result.error(SystemConstants.HTTP_BAD_REQUEST, "上传文件不能超过 20MB"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.error("参数错误: {}", e.getMessage());
        return Result.error(SystemConstants.HTTP_BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("系统异常，请联系管理员");
    }
}
