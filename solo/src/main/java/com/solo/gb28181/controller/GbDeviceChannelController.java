package com.solo.gb28181.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.GbDeviceChannelBatchDeleteRequest;
import com.solo.gb28181.request.GbDeviceChannelPageRequest;
import com.solo.gb28181.response.GbDeviceChannelResponse;
import com.solo.gb28181.response.MapMarkerResponse;
import com.solo.gb28181.service.GbDeviceChannelService;
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
 * <p>通道入库由 Catalog 同步自动完成，此外提供页面手动增删改。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /page} — 分页查询通道</li>
 *   <li>{@code GET /by-device/{deviceId}} — 按设备国标ID列出全部通道（设备详情页用）</li>
 *   <li>{@code GET /map-markers} — 首页地图打点：按视口矩形范围返回 marker 列表 + 全库有坐标的通道总数</li>
 *   <li>{@code POST /save} — 手动新增通道（状态与来源由后端强制设定）</li>
 *   <li>{@code POST /update} — 手动编辑通道资料（状态/来源不受影响）</li>
 *   <li>{@code DELETE /{id}} — 删除通道</li>
 *   <li>{@code POST /batch/delete} — 批量删除通道</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/gb28181/device-channel")
@RequiredArgsConstructor
public class GbDeviceChannelController {

    private final GbDeviceChannelService channelService;

    /**
     * 分页查询通道
     * <p>分页参数：{@code page} 从 1 开始，{@code pageSize} 默认 20。
     * 响应结构为 {@code { total, rows }}，前端表格可直接接。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"gb28181:device-channel:list"})
    public Result<PageResult<GbDeviceChannelResponse>> page(@RequestBody GbDeviceChannelPageRequest request) {
        Page<GbDeviceChannelResponse> page = channelService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 按设备国标ID列出全部通道（设备详情页用）
     * <p>{@code deviceId} 为路径参数：设备国标 20 位 ID。
     * 仅返回通道（不含分组），按通道国标 ID 升序。</p>
     */
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
     * 同时返回数据库里有坐标的通道总数（不受视口限制）。
     * <p>单次最多 5000 条，超出在 SQL 内截断。前端按需提示用户放大以查看更多。</p>
     *
     * @param minLng 视口最小经度（瓦片源同系）
     * @param minLat 视口最小纬度
     * @param maxLng 视口最大经度
     * @param maxLat 视口最大纬度
     */
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
     * <p>状态与来源由后端强制设定，前端入参的这两个字段会被忽略。</p>
     *
     * @param channel 通道完整资料（deviceId / channelId / name 必填）
     */
    @PostMapping("/save")
    @RequirePermission({"gb28181:device-channel:add"})
    public Result<Void> save(@RequestBody GbDeviceChannel channel) {
        channelService.createByManual(channel);
        return Result.success();
    }

    /**
     * 手动编辑通道资料
     * <p>仅修改基础资料；在线状态 / 来源字段不受影响。
     * 入参 {@code id} 必填，否则返回 400。</p>
     */
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
     * @param id 通道主键 ID（数据库自增 ID，非国标 channelId）
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"gb28181:device-channel:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        channelService.deleteById(id);
        return Result.success();
    }

    /**
     * 批量删除通道
     * @param request 含 ids 字段：通道主键 ID 列表
     */
    @PostMapping("/batch/delete")
    @RequirePermission({"gb28181:device-channel:delete"})
    @Transactional
    public Result<Void> batchDelete(@RequestBody GbDeviceChannelBatchDeleteRequest request) {
        channelService.deleteBatch(request.getIds());
        return Result.success();
    }

}
