package com.solo.openapi.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 开放 API scope 目录项。由 {@code @RequireScope} 注解在启动时扫描生成，仅用于管理端展示授权选项。
 */
@Schema(description = "开放API scope目录项")
public record OpenApiScopeResponse(
        @Schema(description = "scope 编码") String code,
        @Schema(description = "展示名称") String name,
        @Schema(description = "所属接口分组") String tag,
        @Schema(description = "该 scope 下的接口列表") List<Endpoint> endpoints) {

    @Schema(description = "scope 下的开放接口")
    public record Endpoint(
            @Schema(description = "HTTP 方法") String method,
            @Schema(description = "接口路径") String path,
            @Schema(description = "接口说明") String summary) {
    }
}
