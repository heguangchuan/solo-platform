package com.solo.openapi.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.enums.BusinessType;
import com.solo.openapi.entity.OpenApp;
import com.solo.openapi.request.OpenAppPageRequest;
import com.solo.openapi.request.OpenAppSaveRequest;
import com.solo.openapi.response.OpenAppCreatedResponse;
import com.solo.openapi.service.OpenApiAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开放 API 应用管理端。走平台内部 JWT 和权限体系，跟第三方调的 /open-api/** 是两回事。
 * 入参出参用独立的 request/response 模型，跟其他模块保持一致，密钥密文绝不下发。
 */
@Tag(name = "开放API应用管理", description = "第三方应用的创建、授权、启停、换钥")
@RestController
@RequestMapping("/api/openapi/app")
@RequiredArgsConstructor
public class OpenAppController {

    private final OpenApiAppService appService;

    @Operation(summary = "分页查询应用")
    @PostMapping("/page")
    @RequirePermission("openapi:app:list")
    public Result<PageResult<OpenApp>> page(@Valid @RequestBody OpenAppPageRequest req) {
        Page<OpenApp> result = appService.pageQuery(req);
        return Result.success(new PageResult<>(result.getTotal(), result.getRecords()));
    }

    @Operation(summary = "应用详情")
    @GetMapping("/{id}")
    @RequirePermission("openapi:app:list")
    public Result<OpenApp> detail(@PathVariable Long id) {
        return Result.success(toView(appService.getDetail(id)));
    }

    @Operation(summary = "创建应用，明文密钥仅此一次返回")
    @OperationLog(module = "开放API应用", type = BusinessType.ADD, description = "创建开放API应用")
    @PostMapping("/create")
    @RequirePermission("openapi:app:add")
    public Result<OpenAppCreatedResponse> create(@Valid @RequestBody OpenAppSaveRequest req) {
        OpenApiAppService.CreatedSecret created = appService.createApp(req);
        return Result.success(new OpenAppCreatedResponse(created.id(), created.appKey(),
                created.appSecret(), created.callbackSecret()));
    }

    @Operation(summary = "编辑应用资料/授权/配额")
    @OperationLog(module = "开放API应用", type = BusinessType.EDIT, description = "编辑开放API应用")
    @PostMapping("/update")
    @RequirePermission("openapi:app:edit")
    public Result<Void> update(@Valid @RequestBody OpenAppSaveRequest req) {
        appService.updateApp(req);
        return Result.success();
    }

    @Operation(summary = "启用应用")
    @OperationLog(module = "开放API应用", type = BusinessType.EDIT, description = "启用开放API应用")
    @PostMapping("/{id}/enable")
    @RequirePermission("openapi:app:edit")
    public Result<Void> enable(@PathVariable Long id) {
        appService.enable(id);
        return Result.success();
    }

    @Operation(summary = "停用应用")
    @OperationLog(module = "开放API应用", type = BusinessType.EDIT, description = "停用开放API应用")
    @PostMapping("/{id}/disable")
    @RequirePermission("openapi:app:edit")
    public Result<Void> disable(@PathVariable Long id) {
        appService.disable(id);
        return Result.success();
    }

    @Operation(summary = "换接口签名密钥，新明文密钥仅此一次返回")
    @OperationLog(module = "开放API应用", type = BusinessType.EDIT, description = "开放API应用换钥")
    @PostMapping("/{id}/rotate-secret")
    @RequirePermission("openapi:app:rotate")
    public Result<OpenAppCreatedResponse> rotateSecret(@PathVariable Long id) {
        OpenApiAppService.CreatedSecret created = appService.rotateSecret(id);
        return Result.success(new OpenAppCreatedResponse(id, created.appKey(), created.appSecret(), null));
    }

    /** 详情查了全字段（含密文），下发前清掉密钥，只回一个"是否已配置回调密钥"的标记 */
    private OpenApp toView(OpenApp app) {
        app.setCallbackSecretSet(app.getCallbackSecretCipher() != null
                && !app.getCallbackSecretCipher().isBlank());
        app.setSecretCipher(null);
        app.setOldSecretCipher(null);
        app.setCallbackSecretCipher(null);
        return app;
    }
}
