package com.solo.system.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录响应体
 *
 * <p>对应接口 {@code POST /api/auth/login}。Token 机制：</p>
 * <ul>
 *   <li>{@link #token}（access）默认 30 分钟有效；过期后用 {@link #refreshToken} 调 {@code POST /api/auth/refresh} 换发</li>
 *   <li>{@link #refreshToken}（refresh）默认 7 天有效；滚动刷新时换发后会同步失效旧的</li>
 *   <li>前端拿到后建议把 access token 放请求头 {@code Authorization: Bearer <token>}；refresh 只在过期换发时使用</li>
 * </ul>
 */
@Schema(description = "登录响应体")
@Data
public class LoginResponse {

    /** JWT 访问 token（必填，access；请求头携带） */
    @Schema(description = "JWT 访问 token")
    private String token;

    /** 刷新 token（必填，refresh；access 过期时换发使用） */
    @Schema(description = "刷新 token")
    private String refreshToken;

    /** 当前用户信息（含是否首次登录强制改密标记） */
    @Schema(description = "当前用户信息")
    private UserInfoVO user;
}
