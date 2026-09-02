package com.solo.system.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 刷新 token 接口响应：返回新的 access / refresh token 对（滚动刷新）
 *
 * <p>对应接口 {@code POST /api/auth/refresh}。
 * 旧 refresh token 同步失效，access 过期后必须用新 refresh 继续换发；refresh 自身 7 天有效。</p>
 */
@Schema(description = "刷新 token 接口响应")
@Data
public class RefreshTokenResponse {

    /** 新的 access token（必填；下次请求替换请求头） */
    @Schema(description = "新的 access token")
    private String token;

    /** 新的 refresh token（必填；旧的同步失效） */
    @Schema(description = "新的 refresh token")
    private String refreshToken;
}
