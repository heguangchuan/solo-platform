package com.solo.gb28181.controller;

import com.solo.aop.RequirePermission;
import com.solo.common.Result;
import com.solo.gb28181.entity.GbPtzPreset;
import com.solo.gb28181.service.GbPtzPresetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 云台预置位控制器 */
@Slf4j
@RestController
@RequestMapping("/api/gb28181/ptz/preset")
@RequiredArgsConstructor
public class GbPtzPresetController {
    private final GbPtzPresetService presetService;

    @GetMapping("/list/{channelDbId}")
    @RequirePermission({"gb28181:device:edit"})
    public Result<List<GbPtzPreset>> list(@PathVariable Long channelDbId) {
        return Result.success(presetService.listByChannel(channelDbId));
    }

    @PostMapping("/save")
    @RequirePermission({"gb28181:device:edit"})
    public Result<Void> save(@RequestBody GbPtzPreset preset) {
        presetService.save(preset);
        return Result.success();
    }

    @PostMapping("/update")
    @RequirePermission({"gb28181:device:edit"})
    public Result<Void> update(@RequestBody GbPtzPreset preset) {
        presetService.updateById(preset);
        return Result.success();
    }

    @DeleteMapping("/delete/{id}")
    @RequirePermission({"gb28181:device:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        presetService.removeById(id);
        return Result.success();
    }

    @PostMapping("/call/{id}")
    @RequirePermission({"gb28181:ptz:control"})
    public Result<Void> call(@PathVariable Long id) {
        presetService.call(id);
        return Result.success();
    }
}
