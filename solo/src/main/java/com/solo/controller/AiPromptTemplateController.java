package com.solo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.entity.AiPromptTemplate;
import com.solo.request.AiPromptTemplatePageRequest;
import com.solo.request.AiPromptTemplateRequest;
import com.solo.service.AiPromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 提示词模板控制器
 */
@RestController
@RequestMapping("/api/ai/prompt-template")
@RequiredArgsConstructor
public class AiPromptTemplateController {

    private final AiPromptTemplateService aiPromptTemplateService;

    /**
     * 分页查询
     */
    @PostMapping("/page")
    @RequirePermission({"ai:promptTemplate:list"})
    public Result<PageResult<AiPromptTemplate>> getPage(@RequestBody AiPromptTemplatePageRequest req) {
        Page<AiPromptTemplate> page = aiPromptTemplateService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 获取所有启用的模板列表
     */
    @GetMapping("/list")
    @RequirePermission({"ai:promptTemplate:list"})
    public Result<List<AiPromptTemplate>> getList() {
        return Result.success(aiPromptTemplateService.getEnabledList());
    }

    /**
     * 获取详情
     */
    @GetMapping("/{id}")
    @RequirePermission({"ai:promptTemplate:list"})
    public Result<AiPromptTemplate> getById(@PathVariable Long id) {
        return Result.success(aiPromptTemplateService.getById(id));
    }

    /**
     * 新增模板
     */
    @PostMapping
    @RequirePermission({"ai:promptTemplate:add"})
    @OperationLog(module = "提示词模板", type = BusinessType.ADD, description = "新增模板")
    public Result<Void> save(@RequestBody AiPromptTemplateRequest request) {
        aiPromptTemplateService.createTemplate(request);
        return Result.success();
    }

    /**
     * 修改模板
     */
    @PutMapping("/{id}")
    @RequirePermission({"ai:promptTemplate:edit"})
    @OperationLog(module = "提示词模板", type = BusinessType.EDIT, description = "修改模板")
    public Result<Void> update(@PathVariable Long id, @RequestBody AiPromptTemplateRequest request) {
        aiPromptTemplateService.updateTemplate(id, request);
        return Result.success();
    }

    /**
     * 删除模板
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"ai:promptTemplate:delete"})
    @OperationLog(module = "提示词模板", type = BusinessType.DELETE, description = "删除模板")
    public Result<Void> delete(@PathVariable Long id) {
        aiPromptTemplateService.removeById(id);
        return Result.success();
    }

    /**
     * 批量删除
     */
    @DeleteMapping("/batch")
    @RequirePermission({"ai:promptTemplate:delete"})
    @OperationLog(module = "提示词模板", type = BusinessType.DELETE, description = "批量删除模板")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        aiPromptTemplateService.removeByIds(ids);
        return Result.success();
    }

    /**
     * 渲染模板
     */
    @PostMapping("/render/{templateCode}")
    @RequirePermission({"ai:promptTemplate:list"})
    public Result<String> render(@PathVariable String templateCode, @RequestBody Map<String, Object> variables) {
        String result = aiPromptTemplateService.renderTemplate(templateCode, variables);
        return Result.success(result);
    }
}
