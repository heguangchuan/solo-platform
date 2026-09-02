package com.solo.gb28181.controller;

import com.solo.aop.RequirePermission;
import com.solo.common.Result;
import com.solo.gb28181.entity.GbPtzPreset;
import com.solo.gb28181.service.GbPtzPresetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 云台预置位控制器
 *
 * <p>提供预置位列表查询、新增/编辑/删除预置位、以及调预置位（{@code call}）。
 * 调预置位通过国标 SIP 信令把设备预置位指令下发到通道。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code GET /list/{channelDbId}} — 按通道主键 ID 列出预置位</li>
 *   <li>{@code POST /save} — 新增预置位</li>
 *   <li>{@code POST /update} — 编辑预置位</li>
 *   <li>{@code DELETE /delete/{id}} — 删除预置位</li>
 *   <li>{@code POST /call/{id}} — 调预置位（让设备云台转到指定位置）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/gb28181/ptz/preset")
@RequiredArgsConstructor
public class GbPtzPresetController {
    private final GbPtzPresetService presetService;

    /**
     * 按通道主键 ID 列出预置位
     * @param channelDbId 通道主键 ID（数据库自增 ID，非国标 channelId）
     */
    @GetMapping("/list/{channelDbId}")
    @RequirePermission({"gb28181:ptz:preset"})
    public Result<List<GbPtzPreset>> list(@PathVariable Long channelDbId) {
        return Result.success(presetService.listByChannel(channelDbId));
    }

    /**
     * 新增预置位
     */
    @PostMapping("/save")
    @RequirePermission({"gb28181:ptz:preset:add"})
    public Result<Void> save(@RequestBody GbPtzPreset preset) {
        presetService.savePreset(preset);
        return Result.success();
    }

    /**
     * 编辑预置位
     */
    @PostMapping("/update")
    @RequirePermission({"gb28181:ptz:preset:edit"})
    public Result<Void> update(@RequestBody GbPtzPreset preset) {
        presetService.updatePreset(preset);
        return Result.success();
    }

    /**
     * 删除预置位
     * @param id 预置位主键 ID
     */
    @DeleteMapping("/delete/{id}")
    @RequirePermission({"gb28181:ptz:preset:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        presetService.deletePreset(id);
        return Result.success();
    }

    /**
     * 调预置位：让设备云台转到指定预置位
     * <p>前端弹窗"调预置位"按钮触发，云台会自动转到该位置（设备端能力）。</p>
     *
     * @param id 预置位主键 ID
     */
    @PostMapping("/call/{id}")
    public Result<Void> call(@PathVariable Long id) {
        presetService.call(id);
        return Result.success();
    }
}
