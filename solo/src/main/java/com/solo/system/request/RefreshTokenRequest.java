package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "刷新 access token 的请求体")
@Data
public class RefreshTokenRequest {

    @Schema(description = "刷新 token")
    private String refreshToken;
}
