package com.solo.gb28181.controller;

import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.Result;
import com.solo.common.enums.BusinessType;
import com.solo.gb28181.entity.GbRecordPlan;
import com.solo.gb28181.service.GbRecordPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "云端录像计划", description = "周录像计划的增删改查与启停")
@Slf4j
@RestController
@RequestMapping("/api/gb28181/record-plan")
@RequiredArgsConstructor
public class GbRecordPlanController {

    private final GbRecordPlanService recordPlanService;

    @Operation(summary = "录像计划列表")
    @GetMapping("/list")
    @RequirePermission("gb28181:record:plan")
    public Result<List<GbRecordPlan>> list(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String channelId,
            @RequestParam(required = false) Integer enabled) {
        return Result.success(recordPlanService.listPlans(deviceId, channelId, enabled));
    }

    @Operation(summary = "录像计划详情（含周时段）")
    @GetMapping("/{id}")
    @RequirePermission("gb28181:record:plan")
    public Result<GbRecordPlan> detail(@PathVariable Long id) {
        return Result.success(recordPlanService.getDetail(id));
    }

    @Operation(summary = "新增录像计划")
    @OperationLog(module = "录像计划", type = BusinessType.ADD, description = "新增录像计划")
    @PostMapping("/save")
    @RequirePermission("gb28181:record:plan:add")
    public Result<Void> save(@RequestBody GbRecordPlan plan) {
        recordPlanService.savePlan(plan);
        return Result.success();
    }

    @Operation(summary = "编辑录像计划（周时段整体覆盖）")
    @OperationLog(module = "录像计划", type = BusinessType.EDIT, description = "编辑录像计划")
    @PostMapping("/update")
    @RequirePermission("gb28181:record:plan:edit")
    public Result<Void> update(@RequestBody GbRecordPlan plan) {
        recordPlanService.updatePlan(plan);
        return Result.success();
    }

    @Operation(summary = "删除录像计划")
    @OperationLog(module = "录像计划", type = BusinessType.DELETE, description = "删除录像计划")
    @DeleteMapping("/delete/{id}")
    @RequirePermission("gb28181:record:plan:delete")
    public Result<Void> delete(@PathVariable Long id) {
        recordPlanService.deletePlan(id);
        return Result.success();
    }

    @Operation(summary = "启用/停用录像计划")
    @OperationLog(module = "录像计划", type = BusinessType.EDIT, description = "启停录像计划")
    @PostMapping("/toggle/{id}")
    @RequirePermission("gb28181:record:plan:edit")
    public Result<Void> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        recordPlanService.toggle(id, enabled);
        return Result.success();
    }
}
