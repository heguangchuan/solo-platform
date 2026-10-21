package com.solo.system.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "刷新 token 接口响应")
@Data
public class RefreshTokenResponse {

    @Schema(description = "新的 access token")
    private String token;

    @Schema(description = "新的 refresh token")
    private String refreshToken;
}
