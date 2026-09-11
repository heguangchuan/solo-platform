package com.solo.gb28181.controller;

import com.solo.aop.RequirePermission;
import com.solo.common.Result;
import com.solo.gb28181.entity.GbPtzCruise;
import com.solo.gb28181.entity.GbPtzCruisePoint;
import com.solo.gb28181.service.GbPtzCruiseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 云台巡航路线管理
 */
@Tag(name = "云台巡航路线管理", description = "云台巡航路线管理")
@Slf4j
@RestController
@RequestMapping("/api/gb28181/ptz/cruise")
@RequiredArgsConstructor
public class GbPtzCruiseController {

    private final GbPtzCruiseService cruiseService;

    @Operation(summary = "按通道主键 ID 列出巡航路线")
    @Parameter(name = "channelDbId", description = "通道主键 ID", in = ParameterIn.PATH)
    @GetMapping("/list/{channelDbId}")
    @RequirePermission("gb28181:ptz:cruise")
    public Result<List<GbPtzCruise>> list(@PathVariable Long channelDbId) {
        return Result.success(cruiseService.listByChannel(channelDbId));
    }

    @Operation(summary = "巡航路线详情（含巡航点列表）")
    @Parameter(name = "id", description = "巡航路线 ID", in = ParameterIn.PATH)
    @GetMapping("/{id}")
    @RequirePermission("gb28181:ptz:cruise")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        GbPtzCruise cruise = cruiseService.getDetail(id);
        List<GbPtzCruisePoint> points = cruiseService.listPoints(id);
        Map<String, Object> result = new HashMap<>();
        result.put("cruise", cruise);
        result.put("points", points);
        return Result.success(result);
    }

    @Operation(summary = "新增巡航路线（含巡航点）")
    @PostMapping("/save")
    @RequirePermission("gb28181:ptz:cruise:add")
    public Result<Void> save(@RequestBody Map<String, Object> body) {
        GbPtzCruise cruise = new GbPtzCruise();
        cruise.setChannelDbId(((Number) body.get("channelDbId")).longValue());
        cruise.setName((String) body.get("name"));
        if (body.get("speed") != null) {
            cruise.setSpeed(((Number) body.get("speed")).intValue());
        }
        if (body.get("staySeconds") != null) {
            cruise.setStaySeconds(((Number) body.get("staySeconds")).intValue());
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pointMaps = (List<Map<String, Object>>) body.get("points");
        List<GbPtzCruisePoint> points = pointMaps != null ? pointMaps.stream().map(m -> {
            GbPtzCruisePoint p = new GbPtzCruisePoint();
            p.setPresetId(((Number) m.get("presetId")).intValue());
            if (m.get("staySeconds") != null) {
                p.setStaySeconds(((Number) m.get("staySeconds")).intValue());
            }
            return p;
        }).toList() : List.of();

        cruiseService.saveCruise(cruise, points);
        return Result.success();
    }

    @Operation(summary = "编辑巡航路线")
    @PostMapping("/update")
    @RequirePermission("gb28181:ptz:cruise:edit")
    public Result<Void> update(@RequestBody GbPtzCruise cruise) {
        cruiseService.updateCruise(cruise);
        return Result.success();
    }

    @Operation(summary = "删除巡航路线")
    @Parameter(name = "id", description = "巡航路线 ID", in = ParameterIn.PATH)
    @DeleteMapping("/delete/{id}")
    @RequirePermission("gb28181:ptz:cruise:delete")
    public Result<Void> delete(@PathVariable Long id) {
        cruiseService.deleteCruise(id);
        return Result.success();
    }

    @Operation(summary = "添加单个巡航点")
    @Parameter(name = "id", description = "巡航路线 ID", in = ParameterIn.PATH)
    @PostMapping("/{id}/add-point")
    @RequirePermission("gb28181:ptz:cruise:edit")
    public Result<Void> addPoint(@PathVariable Long id, @RequestBody GbPtzCruisePoint point) {
        cruiseService.addPoint(id, point);
        return Result.success();
    }

    @Operation(summary = "删除单个巡航点")
    @Parameter(name = "id", description = "巡航路线 ID", in = ParameterIn.PATH)
    @Parameter(name = "presetId", description = "预置位编号", in = ParameterIn.PATH)
    @DeleteMapping("/{id}/remove-point/{presetId}")
    @RequirePermission("gb28181:ptz:cruise:edit")
    public Result<Void> removePoint(@PathVariable Long id, @PathVariable Integer presetId) {
        cruiseService.removePoint(id, presetId);
        return Result.success();
    }

    @Operation(summary = "开始巡航")
    @Parameter(name = "id", description = "巡航路线 ID", in = ParameterIn.PATH)
    @PostMapping("/start/{id}")
    @RequirePermission("gb28181:ptz:control")
    public Result<Void> start(@PathVariable Long id) {
        cruiseService.startCruise(id);
        return Result.success();
    }

    @Operation(summary = "停止巡航")
    @Parameter(name = "id", description = "巡航路线 ID", in = ParameterIn.PATH)
    @PostMapping("/stop/{id}")
    @RequirePermission("gb28181:ptz:control")
    public Result<Void> stop(@PathVariable Long id) {
        cruiseService.stopCruise(id);
        return Result.success();
    }
}
