package com.solo.common;

/**
 * 系统常量定义
 * 统一管理状态值、错误码、默认值等
 */
public final class SystemConstants {

    /**
     * 用户/角色/权限状态常量
     */
    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ENABLED = 1;

    /**
     * 默认密码（MD5加密后的 admin123）—— 仅 admin 账号的初始密码使用
     */
    public static final String DEFAULT_PASSWORD = "0192023a7bbd73250516f069df18b500";

    /**
     * 普通账号默认密码模板：{username}@123456
     */
    public static final String DEFAULT_PASSWORD_TEMPLATE = "%s@123456";

    /**
     * 是否首次登录强制改密
     */
    public static final int PWD_RESET_NOT_REQUIRED = 0;
    public static final int PWD_RESET_REQUIRED = 1;

    /**
     * HTTP 状态码
     */
    public static final int HTTP_OK = 200;
    public static final int HTTP_UNAUTHORIZED = 401;
    public static final int HTTP_FORBIDDEN = 403;
    public static final int HTTP_NOT_FOUND = 404;
    public static final int HTTP_BAD_REQUEST = 400;
    public static final int HTTP_INTERNAL_ERROR = 500;
    public static final int HTTP_TOO_MANY_REQUESTS = 429;

    /**
     * Token 响应头
     */
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final int TOKEN_PREFIX_LENGTH = 7;
    public static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";

    /**
     * 权限类型常量
     */
    public static final int PERMISSION_TYPE_MENU = 1;
    public static final int PERMISSION_TYPE_BUTTON = 2;
    public static final int PERMISSION_TYPE_API = 3;

    /**
     * 默认值
     */
    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int DEFAULT_SORT = 0;
    public static final Long DEFAULT_PARENT_ID = 0L;

    private SystemConstants() {
        // 防止实例化
    }
}
