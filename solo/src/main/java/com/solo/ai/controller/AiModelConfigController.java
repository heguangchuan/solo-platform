package com.solo.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.enums.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.ai.entity.AiModelConfig;
import com.solo.ai.llm.AiModelProvider;
import com.solo.ai.request.AiModelConfigPageRequest;
import com.solo.ai.request.AiModelConfigRequest;
import com.solo.ai.response.AiModelConfigResponse;
import com.solo.ai.response.AiModelProviderOptionResponse;
import com.solo.ai.service.AiModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * AI 模型配置管理
 */
@RestController
@RequestMapping("/api/ai/model-config")
@RequiredArgsConstructor
public class AiModelConfigController {

    private final AiModelConfigService aiModelConfigService;

    /**
     * 分页查询模型配置
     */
    @PostMapping("/page")
    @RequirePermission({"ai:modelConfig:list"})
    public Result<PageResult<AiModelConfigResponse>> getPage(@RequestBody AiModelConfigPageRequest req) {
        Page<AiModelConfig> page = aiModelConfigService.pageQuery(req);
        List<AiModelConfigResponse> rows = page.getRecords().stream().map(AiModelConfigResponse::from).toList();
        return Result.success(new PageResult<>(page.getTotal(), rows));
    }

    /**
     * 获取所有启用的配置列表
     */
    @GetMapping("/list")
    @RequirePermission({"ai:modelConfig:list"})
    public Result<List<AiModelConfigResponse>> getList() {
        List<AiModelConfigResponse> rows = aiModelConfigService.getEnabledList().stream().map(AiModelConfigResponse::from).toList();
        return Result.success(rows);
    }

    /**
     * 获取支持的厂商选项
     */
    @GetMapping("/provider-options")
    @RequirePermission({"ai:modelConfig:list"})
    public Result<List<AiModelProviderOptionResponse>> getProviderOptions() {
        List<AiModelProviderOptionResponse> rows = Arrays.stream(AiModelProvider.values())
                .map(AiModelProviderOptionResponse::from)
                .toList();
        return Result.success(rows);
    }

    /**
     * 配置详情
     *
     * @param id 主键 ID
     */
    @GetMapping("/{id}")
    @RequirePermission({"ai:modelConfig:list"})
    public Result<AiModelConfigResponse> getById(@PathVariable Long id) {
        return Result.success(AiModelConfigResponse.from(aiModelConfigService.getById(id)));
    }

    /**
     * 新增配置
     */
    @PostMapping
    @RequirePermission({"ai:modelConfig:add"})
    @OperationLog(module = "AI模型配置", type = BusinessType.ADD, description = "新增模型配置", recordParam = false)
    public Result<Void> save(@RequestBody AiModelConfigRequest request) {
        aiModelConfigService.createConfig(request);
        return Result.success();
    }

    /**
     * 修改配置
     *
     * @param id 主键 ID
     */
    @PutMapping("/{id}")
    @RequirePermission({"ai:modelConfig:edit"})
    @OperationLog(module = "AI模型配置", type = BusinessType.EDIT, description = "修改模型配置", recordParam = false)
    public Result<Void> update(@PathVariable Long id, @RequestBody AiModelConfigRequest request) {
        aiModelConfigService.updateConfig(id, request);
        return Result.success();
    }

    /**
     * 删除配置
     *
     * @param id 主键 ID
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"ai:modelConfig:delete"})
    @OperationLog(module = "AI模型配置", type = BusinessType.DELETE, description = "删除模型配置")
    public Result<Void> delete(@PathVariable Long id) {
        aiModelConfigService.removeById(id);
        return Result.success();
    }

    /**
     * 批量删除配置
     */
    @DeleteMapping("/batch")
    @RequirePermission({"ai:modelConfig:delete"})
    @OperationLog(module = "AI模型配置", type = BusinessType.DELETE, description = "批量删除模型配置")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        aiModelConfigService.removeByIds(ids);
        return Result.success();
    }
}
