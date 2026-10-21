package com.solo.gb28181.controller;

import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.enums.BusinessType;
import com.solo.common.Result;
import com.solo.gb28181.entity.GbPtzPreset;
import com.solo.gb28181.service.GbPtzPresetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "云台预置位管理", description = "云台预置位管理")
@Slf4j
@RestController
@RequestMapping("/api/gb28181/ptz/preset")
@RequiredArgsConstructor
public class GbPtzPresetController {
    private final GbPtzPresetService presetService;

    @Operation(summary = "按通道主键 ID 列出预置位")
    @Parameter(name = "channelDbId", description = "通道主键 ID", in = ParameterIn.PATH)
    @GetMapping("/list/{channelDbId}")
    @RequirePermission({"gb28181:ptz:preset"})
    public Result<List<GbPtzPreset>> list(@PathVariable Long channelDbId) {
        return Result.success(presetService.listByChannel(channelDbId));
    }

    @Operation(summary = "新增预置位")
    @OperationLog(module = "云台预置位", type = BusinessType.ADD, description = "新增预置位")
    @PostMapping("/save")
    @RequirePermission({"gb28181:ptz:preset:add"})
    public Result<Void> save(@RequestBody GbPtzPreset preset) {
        presetService.savePreset(preset);
        return Result.success();
    }

    @Operation(summary = "删除预置位")
    @OperationLog(module = "云台预置位", type = BusinessType.DELETE, description = "删除预置位")
    @Parameter(name = "id", description = "预置位主键 ID", in = ParameterIn.PATH)
    @DeleteMapping("/delete/{id}")
    @RequirePermission({"gb28181:ptz:preset:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        presetService.deletePreset(id);
        return Result.success();
    }

    @Operation(summary = "调预置位：让设备云台转到指定预置位")
    @OperationLog(module = "云台预置位", type = BusinessType.OTHER, description = "调用预置位")
    @Parameter(name = "id", description = "预置位主键 ID", in = ParameterIn.PATH)
    @PostMapping("/call/{id}")
    public Result<Void> call(@PathVariable Long id) {
        presetService.call(id);
        return Result.success();
    }
}
