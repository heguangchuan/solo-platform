package com.solo.openapi.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 创建应用/换钥时一次性返回的明文凭证。
 * 密钥只在这一次响应里出现，后台不提供再次查看，掉了就换钥。
 */
@Schema(description = "应用明文凭证（仅返回一次）")
public record OpenAppCreatedVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "应用标识") String appKey,
        @Schema(description = "接口签名密钥明文") String appSecret,
        @Schema(description = "Webhook 回调签名密钥明文，仅创建应用时返回") String callbackSecret) {
}
