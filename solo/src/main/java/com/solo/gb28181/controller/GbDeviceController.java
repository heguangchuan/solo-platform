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
import com.solo.gb28181.response.GbDeviceStatusCountVO;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.support.CatalogSyncStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "国标设备管理", description = "国标设备管理")
@RestController
@RequestMapping("/api/gb28181/device")
@RequiredArgsConstructor
public class GbDeviceController {

    private final GbDeviceService gbDeviceService;
    private final GbDeviceChannelService gbDeviceChannelService;
    private final CatalogSyncStatusService catalogSyncStatusService;

    @Operation(summary = "分页查询设备")
    @PostMapping("/page")
    @RequirePermission({"gb28181:device:list"})
    public Result<PageResult<GbDevice>> page(@RequestBody GbDevicePageRequest request) {
        Page<GbDevice> page = gbDeviceService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }


    @Operation(summary = "按状态统计设备数")
    @GetMapping("/status-count")
    @RequirePermission({"gb28181:device:list"})
    public Result<List<GbDeviceStatusCountVO>> statusCount() {
        return Result.success(gbDeviceService.statusCount());
    }


    @Operation(summary = "按关键词搜索设备")
    @GetMapping("/search")
    @RequirePermission({"gb28181:device:list"})
    public Result<PageResult<GbDevice>> search(
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") Long page,
            @Parameter(description = "每页条数，默认20") @RequestParam(defaultValue = "20") Long pageSize) {
        Page<GbDevice> result = gbDeviceService.searchByKeyword(keyword, page, pageSize);
        return Result.success(new PageResult<>(result.getTotal(), result.getRecords()));
    }

    @Operation(summary = "设备详情")
    @Parameter(name = "deviceId", description = "设备国标 20 位 ID", in = ParameterIn.PATH)
    @GetMapping("/{deviceId}")
    @RequirePermission({"gb28181:device:list"})
    public Result<GbDevice> detail(@PathVariable String deviceId) {
        GbDevice device = gbDeviceService.lambdaQuery().eq(GbDevice::getDeviceId, deviceId).one();
        if (device == null) {
            throw new ServiceException("设备不存在: " + deviceId);
        }
        return Result.success(device);
    }

    @Operation(summary = "手动新增设备")
    @PostMapping("/save")
    @RequirePermission({"gb28181:device:add"})
    public Result<Void> save(@RequestBody GbDeviceCreateWithChannelsRequest request) {
        gbDeviceService.createByManual(request.getDevice(), request.getChannels());
        return Result.success();
    }

    @Operation(summary = "手动编辑设备资料")
    @PostMapping("/update")
    @RequirePermission({"gb28181:device:edit"})
    public Result<Void> update(@RequestBody GbDevice device) {
        if (device.getId() == null) {
            throw new ServiceException("设备ID不能为空");
        }
        gbDeviceService.updateByManual(device);
        return Result.success();
    }

    @Operation(summary = "手动触发设备信息 + 通道目录同步")
    @Parameter(name = "deviceId", description = "设备国标 20 位 ID", in = ParameterIn.PATH)
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

    @Operation(summary = "拉取当前设备同步进度")
    @Parameter(name = "deviceId", description = "设备国标 20 位 ID", in = ParameterIn.PATH)
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

    @Operation(summary = "批量探测设备存活")
    @PostMapping("/detect")
    @RequirePermission({"gb28181:device:sync"})
    public Result<GbDeviceDetectResponse> detect(@RequestBody GbDeviceDetectRequest request) {
        return Result.success(gbDeviceService.detectBatch(request.getIds()));
    }

    @Operation(summary = "删除设备记录，连同其名下所有通道")
    @Parameter(name = "deviceId", description = "设备国标 20 位 ID", in = ParameterIn.PATH)
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

    @Operation(summary = "批量删除设备记录，连同每台设备名下所有通道")
    @PostMapping("/batch/delete")
    @RequirePermission({"gb28181:device:delete"})
    @Transactional
    public Result<Void> batchDelete(@RequestBody GbDeviceBatchDeleteRequest request) {
        gbDeviceService.deleteBatch(request.getIds());
        return Result.success();
    }

    @Schema(description = "同步触发响应")
    public record SyncTriggerResponse(
            @Schema(description = "触发结果：TRIGGERED / ALREADY_RUNNING")
            String result,
            @Schema(description = "设备国标 20 位 ID")
            String deviceId,
            @Schema(description = "当前同步进度 JSON 字符串")
            String status) {}
}
