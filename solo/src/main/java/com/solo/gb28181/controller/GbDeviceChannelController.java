package com.solo.gb28181.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.GbDeviceChannelBatchDeleteRequest;
import com.solo.gb28181.request.GbDeviceChannelPageRequest;
import com.solo.gb28181.response.GbChannelStatusCountVO;
import com.solo.gb28181.response.GbDeviceChannelResponse;
import com.solo.gb28181.response.MapMarkerResponse;
import com.solo.gb28181.service.GbDeviceChannelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 国标设备通道管理
 */
@Tag(name = "国标设备通道管理", description = "国标设备通道管理")
@RestController
@RequestMapping("/api/gb28181/device-channel")
@RequiredArgsConstructor
public class GbDeviceChannelController {

    private final GbDeviceChannelService channelService;

    /**
     * 分页查询通道
     */
    @Operation(summary = "分页查询通道")
    @PostMapping("/page")
    @RequirePermission({"gb28181:device-channel:list"})
    public Result<PageResult<GbDeviceChannelResponse>> page(@RequestBody GbDeviceChannelPageRequest request) {
        Page<GbDeviceChannelResponse> page = channelService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 按状态统计通道数（全局口径，与查询条件无关；按 parental 区分通道/分组）
     */
    @Operation(summary = "按状态统计通道数")
    @GetMapping("/status-count")
    @RequirePermission({"gb28181:device-channel:list"})
    public Result<List<GbChannelStatusCountVO>> statusCount() {
        return Result.success(channelService.statusCount());
    }

    /**
     * 按设备国标ID列出全部通道
     *
     * @param deviceId 设备国标 20 位 ID
     */
    @Operation(summary = "按设备国标ID列出全部通道")
    @Parameter(name = "deviceId", description = "设备国标 20 位 ID", in = ParameterIn.PATH)
    @GetMapping("/by-device/{deviceId}")
    @RequirePermission({"gb28181:device-channel:list"})
    public Result<List<GbDeviceChannel>> listByDevice(@PathVariable String deviceId) {
        List<GbDeviceChannel> list = channelService.lambdaQuery()
                .eq(GbDeviceChannel::getDeviceId, deviceId)
                .eq(GbDeviceChannel::getParental, 0)
                .orderByAsc(GbDeviceChannel::getChannelId)
                .list();
        return Result.success(list);
    }

    /**
     * 首页地图打点：按当前视口矩形范围返回该范围内有坐标的通道 marker 列表，
     *
     * @param minLng 视口最小经度
     * @param minLat 视口最小纬度
     * @param maxLng 视口最大经度
     * @param maxLat 视口最大纬度
     */
    @Operation(summary = "首页地图打点：按视口范围返回 marker 列表")
    @Parameter(name = "minLng", description = "视口最小经度")
    @Parameter(name = "minLat", description = "视口最小纬度")
    @Parameter(name = "maxLng", description = "视口最大经度")
    @Parameter(name = "maxLat", description = "视口最大纬度")
    @GetMapping("/map-markers")
    @RequirePermission({"gb28181:device-channel:list"})
    public Result<MapMarkerResponse> mapMarkers(@RequestParam double minLng,
                                                @RequestParam double minLat,
                                                @RequestParam double maxLng,
                                                @RequestParam double maxLat) {
        return Result.success(channelService.queryMapMarkers(minLng, minLat, maxLng, maxLat));
    }

    /**
     * 手动新增通道
     *
     * @param channel 通道完整资料
     */
    @Operation(summary = "手动新增通道")
    @PostMapping("/save")
    @RequirePermission({"gb28181:device-channel:add"})
    public Result<Void> save(@RequestBody GbDeviceChannel channel) {
        channelService.createByManual(channel);
        return Result.success();
    }

    /**
     * 手动编辑通道资料
     */
    @Operation(summary = "手动编辑通道资料")
    @PostMapping("/update")
    @RequirePermission({"gb28181:device-channel:edit"})
    public Result<Void> update(@RequestBody GbDeviceChannel channel) {
        if (channel.getId() == null) {
            throw new ServiceException("通道ID不能为空");
        }
        channelService.updateByManual(channel);
        return Result.success();
    }

    /**
     * 删除通道
     *
     * @param id 通道主键 ID
     */
    @Operation(summary = "删除通道")
    @Parameter(name = "id", description = "通道主键 ID", in = ParameterIn.PATH)
    @DeleteMapping("/{id}")
    @RequirePermission({"gb28181:device-channel:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        channelService.deleteById(id);
        return Result.success();
    }

    /**
     * 批量删除通道
     *
     * @param request 含 ids 字段：通道主键 ID 列表
     */
    @Operation(summary = "批量删除通道")
    @PostMapping("/batch/delete")
    @RequirePermission({"gb28181:device-channel:delete"})
    @Transactional
    public Result<Void> batchDelete(@RequestBody GbDeviceChannelBatchDeleteRequest request) {
        channelService.deleteBatch(request.getIds());
        return Result.success();
    }

}
