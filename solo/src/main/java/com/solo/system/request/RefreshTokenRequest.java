package com.solo.system.request;

import lombok.Data;

/**
 * 刷新 access token 的请求体
 */
@Data
public class RefreshTokenRequest {

    /**
     * 登录时签发的 refresh token
     */
    private String refreshToken;
}
