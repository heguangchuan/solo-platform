package com.solo.openapi.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "应用明文凭证（仅返回一次）")
public record OpenAppCreatedResponse(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "应用标识") String appKey,
        @Schema(description = "接口签名密钥明文") String appSecret,
        @Schema(description = "Webhook 回调签名密钥明文，仅创建应用时返回") String callbackSecret) {
}
