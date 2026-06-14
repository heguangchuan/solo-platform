package com.solo.request;

import lombok.Data;

/**
 * 用户登录请求体
 * 包含登录所需的用户名和密码参数
 */
@Data
public class LoginRequest {

    /**
     * 登录用户名
     */
    private String username;

    /**
     * 登录密码（明文，服务端加密校验）
     */
    private String password;
}
