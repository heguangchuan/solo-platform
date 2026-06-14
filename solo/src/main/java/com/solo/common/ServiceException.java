package com.solo.common;

/**
 * 自定义业务异常类
 * 当业务逻辑校验失败时抛出此异常，由全局异常处理器统一捕获并返回错误响应
 */
public class ServiceException extends RuntimeException {

    /**
     * 业务错误码
     */
    private int code;

    /**
     * 无参构造方法（供序列化使用）
     */
    public ServiceException() {
        super();
    }

    /**
     * 构造业务异常
     *
     * @param message 错误提示信息
     */
    public ServiceException(String message) {
        super(message);
        this.code = 500;
    }

    /**
     * 构造业务异常
     *
     * @param code    错误状态码
     * @param message 错误提示信息
     */
    public ServiceException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造业务异常
     *
     * @param message 错误提示信息
     * @param cause   异常原因
     */
    public ServiceException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }

    /**
     * 构造业务异常
     *
     * @param code    错误状态码
     * @param message 错误提示信息
     * @param cause   异常原因
     */
    public ServiceException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 获取错误状态码
     *
     * @return 错误码
     */
    public int getCode() {
        return code;
    }
}
