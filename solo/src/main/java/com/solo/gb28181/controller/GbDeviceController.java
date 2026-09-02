package com.solo.gb28181.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.request.GbDeviceCreateWithChannelsRequest;
import com.solo.gb28181.request.GbDeviceBatchDeleteRequest;
import com.solo.gb28181.request.GbDeviceDetectRequest;
import com.solo.gb28181.request.GbDevicePageRequest;
import com.solo.gb28181.response.GbDeviceDetectResponse;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.support.CatalogSyncStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 国标设备管理
 */
@RestController
@RequestMapping("/api/gb28181/device")
@RequiredArgsConstructor
public class GbDeviceController {

    private final GbDeviceService gbDeviceService;
    private final GbDeviceChannelService gbDeviceChannelService;
    private final CatalogSyncStatusService catalogSyncStatusService;

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
     * 设备详情
     *
     * @param deviceId 设备国标 20 位 ID
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
     * 手动新增设备，同时可新增多条通道
     */
    @PostMapping("/save")
    @RequirePermission({"gb28181:device:add"})
    public Result<Void> save(@RequestBody GbDeviceCreateWithChannelsRequest request) {
        gbDeviceService.createByManual(request.getDevice(), request.getChannels());
        return Result.success();
    }

    /**
     * 手动编辑设备资料
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
     *
     * @param deviceId 设备国标 20 位 ID
     */
    @PostMapping("/{deviceId}/sync")
    @RequirePermission({"gb28181:device:sync"})
    public Result<SyncTriggerResponse> sync(@PathVariable String deviceId) {
        // 先检查设备状态，给出精确错误
        GbDevice device = gbDeviceService.lambdaQuery().eq(GbDevice::getDeviceId, deviceId).one();
        if (device == null) {
            throw new ServiceException("设备不存在: " + deviceId);
        }
        if (device.getStatus() == null || device.getStatus() != 1) {
            throw new ServiceException("设备离线，无法同步");
        }
        try {
            catalogSyncStatusService.trigger(deviceId);
            return Result.success(new SyncTriggerResponse("TRIGGERED", deviceId,
                    catalogSyncStatusService.getRawStatus(deviceId)));
        } catch (IllegalStateException e) {
            if ("ALREADY_RUNNING".equals(e.getMessage())) {
                return Result.success(new SyncTriggerResponse("ALREADY_RUNNING", deviceId,
                        catalogSyncStatusService.getRawStatus(deviceId)));
            }
            throw e;
        }
    }

    /**
     * 拉取当前设备同步进度
     *
     * @param deviceId 设备国标 20 位 ID
     */
    @GetMapping("/{deviceId}/sync-status")
    @RequirePermission({"gb28181:device:sync"})
    public Result<String> syncStatus(@PathVariable String deviceId) {
        String raw = catalogSyncStatusService.getRawStatus(deviceId);
        if (raw == null) {
            return Result.success("{\"deviceId\":\"" + deviceId
                    + "\",\"status\":\"IDLE\",\"received\":0,\"total\":0,\"percent\":0.0}");
        }
        return Result.success(raw);
    }

    /**
     * 批量探测设备存活
     */
    @PostMapping("/detect")
    @RequirePermission({"gb28181:device:sync"})
    public Result<GbDeviceDetectResponse> detect(@RequestBody GbDeviceDetectRequest request) {
        return Result.success(gbDeviceService.detectBatch(request.getIds()));
    }

    /**
     * 删除设备记录，连同其名下所有通道
     *
     * @param deviceId 设备国标 20 位 ID
     */
    @DeleteMapping("/{deviceId}")
    @RequirePermission({"gb28181:device:delete"})
    @Transactional
    public Result<Void> delete(@PathVariable String deviceId) {
        GbDevice device = gbDeviceService.lambdaQuery().eq(GbDevice::getDeviceId, deviceId).one();
        if (device != null) {
            gbDeviceChannelService.deleteByDeviceId(device.getDeviceId());
            gbDeviceService.removeById(device.getId());
        }
        return Result.success();
    }

    /**
     * 批量删除设备记录，连同每台设备名下所有通道
     */
    @PostMapping("/batch/delete")
    @RequirePermission({"gb28181:device:delete"})
    @Transactional
    public Result<Void> batchDelete(@RequestBody GbDeviceBatchDeleteRequest request) {
        gbDeviceService.deleteBatch(request.getIds());
        return Result.success();
    }

    /**
     * 同步触发响应。
     */
    public record SyncTriggerResponse(
            /**
             * 触发结果：`TRIGGERED` / `ALREADY_RUNNING`
             */
            String result,
            /**
             * 设备国标 20 位 ID
             */
            String deviceId,
            /**
             * 当前同步进度 JSON 字符串
             */
            String status) {}
}
