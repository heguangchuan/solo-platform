package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "刷新 access token 的请求体")
@Data
public class RefreshTokenRequest {

    @Schema(description = "刷新 token")
    @NotBlank
    @Size(max = 4096)
    private String refreshToken;
}
