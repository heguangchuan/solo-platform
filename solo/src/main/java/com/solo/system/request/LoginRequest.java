package com.solo.system.request;

import lombok.Data;

/**
 * 用户登录请求体
 *
 * <p>对应接口 {@code POST /api/auth/login}。
 * 失败常见返回：401（账号或密码错误）、429（5 次/分钟限流）。</p>
 */
@Data
public class LoginRequest {

    /** 登录账号（必填） */
    private String username;

    /** 登录密码（必填，明文传输；HTTPS 保护；服务端 MD5 后比对） */
    private String password;
}
