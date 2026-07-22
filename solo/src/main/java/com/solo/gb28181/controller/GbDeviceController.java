package com.solo.gb28181.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.request.GbDeviceCreateWithChannelsRequest;
import com.solo.gb28181.request.GbDeviceBatchDeleteRequest;
import com.solo.gb28181.request.GbDevicePageRequest;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.transmit.cmd.SipCommander;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 国标设备管理
 * 设备的入库由 SIP REGISTER 流程自动完成，这里只提供查询/删除/手动同步
 */
@RestController
@RequestMapping("/api/gb28181/device")
@RequiredArgsConstructor
public class GbDeviceController {

    private final GbDeviceService gbDeviceService;
    private final GbDeviceChannelService gbDeviceChannelService;
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
        GbDevice device = gbDeviceService.lambdaQuery().eq(GbDevice::getDeviceId, deviceId).one();
        if (device == null) {
            throw new ServiceException("设备不存在: " + deviceId);
        }
        return Result.success(device);
    }

    /**
     * 手动新增设备（页面录入），同时可新增多条通道。状态与来源由后端强制设定，忽略入参。
     */
    @PostMapping("/save")
    @RequirePermission({"gb28181:device:add"})
    public Result<Void> save(@RequestBody GbDeviceCreateWithChannelsRequest request) {
        gbDeviceService.createByManual(request.getDevice(), request.getChannels());
        return Result.success();
    }

    /**
     * 手动编辑设备资料。状态/来源及 SIP 运行期字段不受影响。
     */
    @PostMapping("/update")
    @RequirePermission({"gb28181:device:edit"})
    public Result<Void> update(@RequestBody GbDevice device) {
        if (device.getId() == null) {
            throw new ServiceException("设备ID不能为空");
        }
        gbDeviceService.updateByManual(device);
        return Result.success();
    }

    /**
     * 手动触发设备信息 + 通道目录同步
     */
    @PostMapping("/{deviceId}/sync")
    @RequirePermission({"gb28181:device:sync"})
    public Result<Void> sync(@PathVariable String deviceId) {
        GbDevice device = gbDeviceService.lambdaQuery().eq(GbDevice::getDeviceId, deviceId).one();
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
     * 删除设备记录，连同其名下所有通道（不影响实际设备）
     */
    @DeleteMapping("/{deviceId}")
    @RequirePermission({"gb28181:device:delete"})
    @Transactional
    public Result<Void> delete(@PathVariable String deviceId) {
        GbDevice device = gbDeviceService.lambdaQuery().eq(GbDevice::getDeviceId, deviceId).one();
        if (device != null) {
            gbDeviceChannelService.deleteByDeviceDbId(device.getId());
            gbDeviceService.removeById(device.getId());
        }
        return Result.success();
    }

    /**
     * 批量删除设备记录，连同每台设备名下所有通道（不影响实际设备）
     */
    @PostMapping("/batch/delete")
    @RequirePermission({"gb28181:device:delete"})
    @Transactional
    public Result<Void> batchDelete(@RequestBody GbDeviceBatchDeleteRequest request) {
        gbDeviceService.deleteBatch(request.getIds());
        return Result.success();
    }
}
