package com.solo.gb28181.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.utils.UserHolderUtils;
import com.solo.gb28181.entity.GbDeviceAlarm;
import com.solo.gb28181.request.GbDeviceAlarmHandleRequest;
import com.solo.gb28181.request.GbDeviceAlarmPageRequest;
import com.solo.gb28181.service.GbDeviceAlarmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "报警中心", description = "设备报警查询与处置")
@Slf4j
@RestController
@RequestMapping("/api/gb28181/alarm")
@RequiredArgsConstructor
public class GbDeviceAlarmController {

    private final GbDeviceAlarmService alarmService;

    @Operation(summary = "分页查询报警")
    @PostMapping("/page")
    @RequirePermission({"gb28181:alarm:list"})
    public Result<PageResult<GbDeviceAlarm>> page(@RequestBody GbDeviceAlarmPageRequest request) {
        Page<GbDeviceAlarm> page = alarmService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    @Operation(summary = "报警详情")
    @Parameter(name = "id", description = "报警主键 ID", in = ParameterIn.PATH)
    @GetMapping("/detail/{id}")
    @RequirePermission({"gb28181:alarm:list"})
    public Result<GbDeviceAlarm> detail(@PathVariable Long id) {
        return Result.success(alarmService.getById(id));
    }

    @Operation(summary = "处置报警（确认/已处置/误报）")
    @PostMapping("/handle")
    @RequirePermission({"gb28181:alarm:handle"})
    public Result<Void> handle(@RequestBody GbDeviceAlarmHandleRequest request) {
        alarmService.handle(request, UserHolderUtils.getCurrentUsername());
        return Result.success();
    }

    @Operation(summary = "删除报警")
    @Parameter(name = "id", description = "报警主键 ID", in = ParameterIn.PATH)
    @DeleteMapping("/delete/{id}")
    @RequirePermission({"gb28181:alarm:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        alarmService.deleteAlarm(id);
        return Result.success();
    }
}
