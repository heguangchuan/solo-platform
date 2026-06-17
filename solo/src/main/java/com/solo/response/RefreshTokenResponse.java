package com.solo.response;

import lombok.Data;

/**
 * 刷新 token 接口响应：返回新的 access / refresh token 对（滚动刷新）
 */
@Data
public class RefreshTokenResponse {

    /**
     * 新的 access token
     */
    private String token;

    /**
     * 新的 refresh token，旧的 refresh token 同步失效
     */
    private String refreshToken;
}
