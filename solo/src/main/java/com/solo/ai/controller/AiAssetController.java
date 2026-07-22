package com.solo.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.enums.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.ai.entity.AiAsset;
import com.solo.ai.request.AiAssetPageRequest;
import com.solo.ai.request.AiAssetRequest;
import com.solo.ai.service.AiAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI素材控制器
 */
@RestController
@RequestMapping("/api/ai/asset")
@RequiredArgsConstructor
public class AiAssetController {

    private final AiAssetService aiAssetService;

    /**
     * 分页查询
     */
    @PostMapping("/page")
    @RequirePermission({"ai:asset:list"})
    public Result<PageResult<AiAsset>> getPage(@RequestBody AiAssetPageRequest req) {
        Page<AiAsset> page = aiAssetService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 获取详情
     */
    @GetMapping("/{id}")
    @RequirePermission({"ai:asset:list"})
    public Result<AiAsset> getById(@PathVariable Long id) {
        return Result.success(aiAssetService.getById(id));
    }

    /**
     * 新增素材
     */
    @PostMapping
    @RequirePermission({"ai:asset:add"})
    @OperationLog(module = "AI素材", type = BusinessType.ADD, description = "新增素材")
    public Result<Void> save(@RequestBody AiAssetRequest request) {
        aiAssetService.createAsset(request);
        return Result.success();
    }

    /**
     * 修改素材
     */
    @PutMapping("/{id}")
    @RequirePermission({"ai:asset:edit"})
    @OperationLog(module = "AI素材", type = BusinessType.EDIT, description = "修改素材")
    public Result<Void> update(@PathVariable Long id, @RequestBody AiAssetRequest request) {
        aiAssetService.updateAsset(id, request);
        return Result.success();
    }

    /**
     * 删除素材
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"ai:asset:delete"})
    @OperationLog(module = "AI素材", type = BusinessType.DELETE, description = "删除素材")
    public Result<Void> delete(@PathVariable Long id) {
        aiAssetService.removeById(id);
        return Result.success();
    }

    /**
     * 批量删除
     */
    @DeleteMapping("/batch")
    @RequirePermission({"ai:asset:delete"})
    @OperationLog(module = "AI素材", type = BusinessType.DELETE, description = "批量删除素材")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        aiAssetService.removeByIds(ids);
        return Result.success();
    }
}
