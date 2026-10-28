package com.solo.openapi.controller;

import com.solo.aop.RequirePermission;
import com.solo.common.Result;
import com.solo.openapi.response.OpenApiScopeResponse;
import com.solo.openapi.support.OpenApiScopeCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 开放 API scope 目录。只读，数据来自启动扫描，管理端用它渲染授权选项。
 */
@Tag(name = "开放API Scope", description = "开放接口授权范围目录")
@RestController
@RequestMapping("/api/openapi/scope")
@RequiredArgsConstructor
public class OpenApiScopeController {

    private final OpenApiScopeCatalog scopeCatalog;

    @Operation(summary = "查询 scope 目录")
    @GetMapping("/list")
    @RequirePermission("openapi:app:list")
    public Result<List<OpenApiScopeResponse>> list() {
        return Result.success(scopeCatalog.getScopes());
    }
}
