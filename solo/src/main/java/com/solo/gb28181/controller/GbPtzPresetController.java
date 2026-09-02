package com.solo.gb28181.controller;

import com.solo.aop.RequirePermission;
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

/**
 * 云台预置位管理
 */
@Tag(name = "云台预置位管理", description = "云台预置位管理")
@Slf4j
@RestController
@RequestMapping("/api/gb28181/ptz/preset")
@RequiredArgsConstructor
public class GbPtzPresetController {
    private final GbPtzPresetService presetService;

    /**
     * 按通道主键 ID 列出预置位
     *
     * @param channelDbId 通道主键 ID
     */
    @Operation(summary = "按通道主键 ID 列出预置位")
    @Parameter(name = "channelDbId", description = "通道主键 ID", in = ParameterIn.PATH)
    @GetMapping("/list/{channelDbId}")
    @RequirePermission({"gb28181:ptz:preset"})
    public Result<List<GbPtzPreset>> list(@PathVariable Long channelDbId) {
        return Result.success(presetService.listByChannel(channelDbId));
    }

    /**
     * 新增预置位
     */
    @Operation(summary = "新增预置位")
    @PostMapping("/save")
    @RequirePermission({"gb28181:ptz:preset:add"})
    public Result<Void> save(@RequestBody GbPtzPreset preset) {
        presetService.savePreset(preset);
        return Result.success();
    }

    /**
     * 编辑预置位
     */
    @Operation(summary = "编辑预置位")
    @PostMapping("/update")
    @RequirePermission({"gb28181:ptz:preset:edit"})
    public Result<Void> update(@RequestBody GbPtzPreset preset) {
        presetService.updatePreset(preset);
        return Result.success();
    }

    /**
     * 删除预置位
     *
     * @param id 预置位主键 ID
     */
    @Operation(summary = "删除预置位")
    @Parameter(name = "id", description = "预置位主键 ID", in = ParameterIn.PATH)
    @DeleteMapping("/delete/{id}")
    @RequirePermission({"gb28181:ptz:preset:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        presetService.deletePreset(id);
        return Result.success();
    }

    /**
     * 调预置位：让设备云台转到指定预置位
     *
     * @param id 预置位主键 ID
     */
    @Operation(summary = "调预置位：让设备云台转到指定预置位")
    @Parameter(name = "id", description = "预置位主键 ID", in = ParameterIn.PATH)
    @PostMapping("/call/{id}")
    public Result<Void> call(@PathVariable Long id) {
        presetService.call(id);
        return Result.success();
    }
}
