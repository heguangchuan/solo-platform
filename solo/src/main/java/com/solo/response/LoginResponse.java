package com.solo.response;

import lombok.Data;

/**
 * 登录响应体
 * 包含JWT Token和用户信息
 */
@Data
public class LoginResponse {

    /**
     * JWT认证Token
     */
    private String token;

    /**
     * 用户信息VO
     */
    private UserInfoVO user;
}
