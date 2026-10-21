package com.solo.gb28181.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.gb28181.entity.GbMobilePosition;
import com.solo.gb28181.request.GbMobilePositionPageRequest;
import com.solo.gb28181.service.GbMobilePositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "移动轨迹", description = "设备 GPS 位置点查询与历史轨迹")
@Slf4j
@RestController
@RequestMapping("/api/gb28181/position")
@RequiredArgsConstructor
public class GbMobilePositionController {

    private final GbMobilePositionService positionService;

    @Operation(summary = "分页查询位置点")
    @PostMapping("/page")
    @RequirePermission({"gb28181:position:list"})
    public Result<PageResult<GbMobilePosition>> page(@RequestBody GbMobilePositionPageRequest request) {
        Page<GbMobilePosition> page = positionService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    @Operation(summary = "按设备+时间段查询历史轨迹（时间升序）")
    @PostMapping("/track")
    @RequirePermission({"gb28181:position:list"})
    public Result<List<GbMobilePosition>> track(@RequestBody GbMobilePositionPageRequest request) {
        return Result.success(positionService.track(request));
    }

    @Operation(summary = "位置点详情")
    @Parameter(name = "id", description = "位置点主键 ID", in = ParameterIn.PATH)
    @GetMapping("/detail/{id}")
    @RequirePermission({"gb28181:position:list"})
    public Result<GbMobilePosition> detail(@PathVariable Long id) {
        return Result.success(positionService.getById(id));
    }

    @Operation(summary = "删除位置点")
    @Parameter(name = "id", description = "位置点主键 ID", in = ParameterIn.PATH)
    @DeleteMapping("/delete/{id}")
    @RequirePermission({"gb28181:position:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        positionService.deletePosition(id);
        return Result.success();
    }
}
