package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 刷新 access token 的请求体
 *
 * <p>对应接口 {@code POST /api/auth/refresh}；access 过期后用 refresh token 换发新 token 对。
 * 失败常见返回：401（refresh 过期或被吊销）、429（10 次/分钟限流）。</p>
 */
@Schema(description = "刷新 access token 的请求体")
@Data
public class RefreshTokenRequest {

    /** 登录时返回的 refresh token（必填） */
    @Schema(description = "刷新 token")
    private String refreshToken;
}
