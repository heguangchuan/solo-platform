package com.solo.response;

import lombok.Data;

/**
 * 登录响应体
 * 包含JWT Token和用户信息
 */
@Data
public class LoginResponse {

    /**
     * JWT认证Token（access token）
     */
    private String token;

    /**
     * 刷新Token（refresh token），过期时间长，用于无感换发新的 access token
     */
    private String refreshToken;

    /**
     * 用户信息VO
     */
    private UserInfoVO user;
}
