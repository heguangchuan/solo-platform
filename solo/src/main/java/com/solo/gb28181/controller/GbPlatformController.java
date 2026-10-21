package com.solo.gb28181.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbPlatform;
import com.solo.gb28181.request.GbPlatformBatchDeleteRequest;
import com.solo.gb28181.request.GbPlatformPageRequest;
import com.solo.gb28181.response.GbPlatformVO;
import com.solo.gb28181.service.GbPlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Tag(name = "国标接入禁用名单管理", description = "国标接入禁用名单管理")
@RestController
@RequestMapping("/api/gb28181/platform")
@RequiredArgsConstructor
public class GbPlatformController {

    private final GbPlatformService gbPlatformService;

    @Operation(summary = "分页查询禁用名单")
    @PostMapping("/page")
    @RequirePermission({"gb28181:platform:list"})
    public Result<PageResult<GbPlatformVO>> page(@RequestBody GbPlatformPageRequest request) {
        Page<GbPlatformVO> page = gbPlatformService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    @Operation(summary = "新增禁用项")
    @PostMapping("/save")
    @RequirePermission({"gb28181:platform:add"})
    public Result<Void> save(@RequestBody GbPlatform platform) {
        gbPlatformService.createByManual(platform);
        return Result.success();
    }

    @Operation(summary = "编辑禁用项")
    @PostMapping("/update")
    @RequirePermission({"gb28181:platform:edit"})
    public Result<Void> update(@RequestBody GbPlatform platform) {
        if (platform.getId() == null) {
            throw new ServiceException("记录ID不能为空");
        }
        gbPlatformService.updateByManual(platform);
        return Result.success();
    }

    @Operation(summary = "移出禁用名单")
    @Parameter(name = "id", description = "禁用项主键 ID", in = ParameterIn.PATH)
    @DeleteMapping("/{id}")
    @RequirePermission({"gb28181:platform:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        gbPlatformService.deleteBatch(java.util.List.of(id));
        return Result.success();
    }

    @Operation(summary = "批量移出禁用名单")
    @PostMapping("/batch/delete")
    @RequirePermission({"gb28181:platform:delete"})
    @Transactional
    public Result<Void> batchDelete(@RequestBody GbPlatformBatchDeleteRequest request) {
        gbPlatformService.deleteBatch(request.getIds());
        return Result.success();
    }
}