package com.solo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.entity.AiModelConfig;
import com.solo.request.AiModelConfigPageRequest;
import com.solo.request.AiModelConfigRequest;
import com.solo.service.AiModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI模型配置控制器
 */
@RestController
@RequestMapping("/api/ai/model-config")
@RequiredArgsConstructor
public class AiModelConfigController {

    private final AiModelConfigService aiModelConfigService;

    /**
     * 分页查询
     */
    @PostMapping("/page")
    @RequirePermission({"ai:modelConfig:list"})
    public Result<PageResult<AiModelConfig>> getPage(@RequestBody AiModelConfigPageRequest req) {
        Page<AiModelConfig> page = aiModelConfigService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 获取所有启用的配置列表
     */
    @GetMapping("/list")
    @RequirePermission({"ai:modelConfig:list"})
    public Result<List<AiModelConfig>> getList() {
        return Result.success(aiModelConfigService.getEnabledList());
    }

    /**
     * 获取详情
     */
    @GetMapping("/{id}")
    @RequirePermission({"ai:modelConfig:list"})
    public Result<AiModelConfig> getById(@PathVariable Long id) {
        return Result.success(aiModelConfigService.getById(id));
    }

    /**
     * 新增配置
     */
    @PostMapping
    @RequirePermission({"ai:modelConfig:add"})
    @OperationLog(module = "AI模型配置", type = BusinessType.ADD, description = "新增模型配置")
    public Result<Void> save(@RequestBody AiModelConfigRequest request) {
        aiModelConfigService.createConfig(request);
        return Result.success();
    }

    /**
     * 修改配置
     */
    @PutMapping("/{id}")
    @RequirePermission({"ai:modelConfig:edit"})
    @OperationLog(module = "AI模型配置", type = BusinessType.EDIT, description = "修改模型配置")
    public Result<Void> update(@PathVariable Long id, @RequestBody AiModelConfigRequest request) {
        aiModelConfigService.updateConfig(id, request);
        return Result.success();
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"ai:modelConfig:delete"})
    @OperationLog(module = "AI模型配置", type = BusinessType.DELETE, description = "删除模型配置")
    public Result<Void> delete(@PathVariable Long id) {
        aiModelConfigService.removeById(id);
        return Result.success();
    }

    /**
     * 批量删除
     */
    @DeleteMapping("/batch")
    @RequirePermission({"ai:modelConfig:delete"})
    @OperationLog(module = "AI模型配置", type = BusinessType.DELETE, description = "批量删除模型配置")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        aiModelConfigService.removeByIds(ids);
        return Result.success();
    }
}
