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
 *
 * <p>设备默认通过 SIP REGISTER 自动入库，本控制器仅提供查询、删除、手动新增/编辑、目录同步入口。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /page} — 分页查询设备</li>
 *   <li>{@code GET /{deviceId}} — 设备详情（按国标 ID）</li>
 *   <li>{@code POST /save} — 手动新增设备并可同步新增多条通道</li>
 *   <li>{@code POST /update} — 手动编辑设备资料（在线状态 / 来源及 SIP 运行期字段不受影响）</li>
 *   <li>{@code POST /{deviceId}/sync} — 手动触发设备信息 + 通道目录同步</li>
 *   <li>{@code GET /{deviceId}/sync-status} — 拉取当前设备同步进度</li>
 *   <li>{@code POST /detect} — 批量探测设备存活</li>
 *   <li>{@code DELETE /{deviceId}} — 删除设备记录，连同其名下所有通道</li>
 *   <li>{@code POST /batch/delete} — 批量删除设备记录，连同每台设备名下所有通道</li>
 * </ul>
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
     * <p>分页参数：{@code page} 从 1 开始，{@code pageSize} 默认 20。
     * 响应结构为 {@code { total, rows }}，前端表格可直接接。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"gb28181:device:list"})
    public Result<PageResult<GbDevice>> page(@RequestBody GbDevicePageRequest request) {
        Page<GbDevice> page = gbDeviceService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 设备详情（按国标 ID）
     * @param deviceId 设备国标 20 位 ID
     * <p>设备不存在返回 400（错误信息含 deviceId）。</p>
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
     * 手动新增设备（页面录入），同时可新增多条通道
     * <p>状态与来源由后端强制设定，前端入参的这两个字段会被忽略。</p>
     */
    @PostMapping("/save")
    @RequirePermission({"gb28181:device:add"})
    public Result<Void> save(@RequestBody GbDeviceCreateWithChannelsRequest request) {
        gbDeviceService.createByManual(request.getDevice(), request.getChannels());
        return Result.success();
    }

    /**
     * 手动编辑设备资料
     * <p>仅修改基础资料；在线状态 / 来源 / SIP 运行期字段不受影响。
     * 入参 {@code id} 必填，否则返回 400。</p>
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
     * <p>5 分钟内重复触发不抛错，直接返回当前状态（前端可无缝看进度）。</p>
     *
     * <h4>响应 {@code result} 取值</h4>
     * <ul>
     *   <li>{@code TRIGGERED}：本次抢到锁，已发起同步</li>
     *   <li>{@code ALREADY_RUNNING}：已有同步在进行中，{@code status} 字段为当前进度</li>
     * </ul>
     *
     * <h4>错误</h4>
     * <ul>
     *   <li>设备不存在 → 400</li>
     *   <li>设备离线 → 400</li>
     * </ul>
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
     * <p>无状态时返回 {@code {"deviceId":"...","status":"IDLE","received":0,"total":0,"percent":0.0}}；
     * 运行中 / 已完成 / 已失败时返回对应 status JSON。status 字段示例：</p>
     * <ul>
     *   <li>{@code RUNNING}：同步中，{@code received}/{@code total}/{@code percent} 推进</li>
     *   <li>{@code DONE}：完成</li>
     *   <li>{@code FAILED}：失败，{@code error} 字段带原因</li>
     * </ul>
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
     * 批量探测设备存活（fire-and-forget）
     *
     * <p>向每台设备发送 SIP 查询，收到应答视为在线，超时视为离线。
     * 探测是异步的，本接口只汇报「已发起多少台、跳过多少台」；
     * 真实在线状态由 SIP 应答 / 超时回调异步写回，前端提示后自行刷新列表查看最新结果。</p>
     */
    @PostMapping("/detect")
    @RequirePermission({"gb28181:device:sync"})
    public Result<GbDeviceDetectResponse> detect(@RequestBody GbDeviceDetectRequest request) {
        return Result.success(gbDeviceService.detectBatch(request.getIds()));
    }

    /**
     * 删除设备记录，连同其名下所有通道
     * <p>仅删本平台数据库记录，不影响实际设备本身。
     * 设备重新 REGISTER 后会重新入库。</p>
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
     * <p>仅删本平台数据库记录，不影响实际设备本身。</p>
     */
    @PostMapping("/batch/delete")
    @RequirePermission({"gb28181:device:delete"})
    @Transactional
    public Result<Void> batchDelete(@RequestBody GbDeviceBatchDeleteRequest request) {
        gbDeviceService.deleteBatch(request.getIds());
        return Result.success();
    }

    /**
     * 同步触发响应（含 result + deviceId + 当前 status JSON）。
     * <p>result 取值：</p>
     * <ul>
     *   <li>TRIGGERED：本次调用已发起同步</li>
     *   <li>ALREADY_RUNNING：已有同步在进行（status 字段为当前进度）</li>
     * </ul>
     */
    public record SyncTriggerResponse(
            /**
             * 触发结果：{@code TRIGGERED} / {@code ALREADY_RUNNING}
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
