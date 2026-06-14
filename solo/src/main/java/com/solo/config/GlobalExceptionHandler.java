package com.solo.config;

import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.common.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 统一捕获和处理系统中的各类异常，返回标准化的错误响应
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     * 当 Service 层抛出 ServiceException 时触发
     *
     * @param e 业务异常
     * @return 包含错误码和消息的响应
     */
    @ExceptionHandler(ServiceException.class)
    public Result<Void> handleServiceException(ServiceException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数错误异常
     * 当请求参数不合法时触发
     *
     * @param e 参数异常
     * @return 错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.error("参数错误: {}", e.getMessage());
        return Result.error(SystemConstants.HTTP_BAD_REQUEST, e.getMessage());
    }

    /**
     * 处理系统未知异常
     * 捕获所有未被显式处理的异常，记录堆栈并返回通用错误
     *
     * @param e 系统异常
     * @return 错误响应
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("系统异常，请联系管理员");
    }
}
