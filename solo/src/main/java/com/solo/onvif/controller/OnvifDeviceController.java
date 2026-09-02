package com.solo.onvif.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.onvif.entity.OnvifDevice;
import com.solo.onvif.request.OnvifDevicePageRequest;
import com.solo.onvif.service.OnvifDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ONVIF 设备管理
 */
@Tag(name = "ONVIF 设备管理", description = "ONVIF 设备管理")
@RestController
@RequestMapping("/api/onvif/device")
@RequiredArgsConstructor
public class OnvifDeviceController {

    private final OnvifDeviceService onvifDeviceService;

    /**
     * 设备分页查询
     */
    @Operation(summary = "设备分页查询")
    @PostMapping("/page")
    @RequirePermission({"onvif:device:list"})
    public Result<PageResult<OnvifDevice>> page(@RequestBody OnvifDevicePageRequest req) {
        Page<OnvifDevice> page = onvifDeviceService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 设备详情
     *
     * @param id 主键 ID
     */
    @Operation(summary = "设备详情")
    @Parameter(name = "id", description = "主键 ID", in = ParameterIn.PATH)
    @GetMapping("/{id}")
    @RequirePermission({"onvif:device:list"})
    public Result<OnvifDevice> detail(@PathVariable Long id) {
        OnvifDevice device = onvifDeviceService.getById(id);
        if (device == null) {
            throw new ServiceException("设备不存在: " + id);
        }
        return Result.success(device);
    }

    /**
     * 新增设备
     */
    @Operation(summary = "新增设备")
    @PostMapping("/save")
    @RequirePermission({"onvif:device:add"})
    public Result<Void> save(@RequestBody OnvifDevice device) {
        device.setId(null); // 强制新增
        onvifDeviceService.saveWithProbe(device);
        return Result.success();
    }

    /**
     * 编辑设备
     */
    @Operation(summary = "编辑设备")
    @PostMapping("/update")
    @RequirePermission({"onvif:device:edit"})
    public Result<Void> update(@RequestBody OnvifDevice device) {
        if (device.getId() == null) {
            throw new ServiceException("设备ID不能为空");
        }
        onvifDeviceService.saveWithProbe(device);
        return Result.success();
    }

    /**
     * 主动探测一次
     *
     * @param id 主键 ID
     */
    @Operation(summary = "主动探测一次")
    @Parameter(name = "id", description = "主键 ID", in = ParameterIn.PATH)
    @PostMapping("/{id}/probe")
    @RequirePermission({"onvif:device:probe"})
    public Result<Void> probe(@PathVariable Long id) {
        onvifDeviceService.probe(id);
        return Result.success();
    }

    /**
     * 删除设备
     *
     * @param id 主键 ID
     */
    @Operation(summary = "删除设备")
    @Parameter(name = "id", description = "主键 ID", in = ParameterIn.PATH)
    @DeleteMapping("/{id}")
    @RequirePermission({"onvif:device:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        onvifDeviceService.removeById(id);
        return Result.success();
    }
}
