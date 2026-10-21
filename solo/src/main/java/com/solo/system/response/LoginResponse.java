package com.solo.system.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "登录响应体")
@Data
public class LoginResponse {

    @Schema(description = "JWT 访问 token")
    private String token;

    @Schema(description = "刷新 token")
    private String refreshToken;

    @Schema(description = "当前用户信息")
    private UserInfoVO user;
}
