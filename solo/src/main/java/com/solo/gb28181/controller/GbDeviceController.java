package com.solo.gb28181.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.request.GbDevicePageRequest;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.transmit.cmd.SipCommander;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 国标设备管理
 * 设备的入库由 SIP REGISTER 流程自动完成，这里只提供查询/删除/手动同步
 */
@RestController
@RequestMapping("/api/gb28181/device")
@RequiredArgsConstructor
public class GbDeviceController {

    private final GbDeviceService gbDeviceService;
    private final SipCommander sipCommander;

    /**
     * 分页查询设备
     */
    @PostMapping("/page")
    @RequirePermission({"gb28181:device:list"})
    public Result<PageResult<GbDevice>> page(@RequestBody GbDevicePageRequest request) {
        Page<GbDevice> page = gbDeviceService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 设备详情（按国标 ID）
     */
    @GetMapping("/{deviceId}")
    @RequirePermission({"gb28181:device:list"})
    public Result<GbDevice> detail(@PathVariable String deviceId) {
        GbDevice device = gbDeviceService.getByDeviceId(deviceId);
        if (device == null) {
            throw new ServiceException("设备不存在: " + deviceId);
        }
        return Result.success(device);
    }

    /**
     * 手动触发设备信息 + 通道目录同步
     */
    @PostMapping("/{deviceId}/sync")
    @RequirePermission({"gb28181:device:sync"})
    public Result<Void> sync(@PathVariable String deviceId) {
        GbDevice device = gbDeviceService.getByDeviceId(deviceId);
        if (device == null) {
            throw new ServiceException("设备不存在: " + deviceId);
        }
        if (device.getStatus() == null || device.getStatus() != 1) {
            throw new ServiceException("设备离线，无法同步");
        }
        sipCommander.queryDeviceInfo(device);
        sipCommander.queryCatalog(device);
        return Result.success();
    }

    /**
     * 删除设备记录（不影响实际设备）
     */
    @DeleteMapping("/{deviceId}")
    @RequirePermission({"gb28181:device:delete"})
    public Result<Void> delete(@PathVariable String deviceId) {
        GbDevice device = gbDeviceService.getByDeviceId(deviceId);
        if (device != null) {
            gbDeviceService.removeById(device.getId());
        }
        return Result.success();
    }
}
