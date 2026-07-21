package com.solo.gb28181.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.GbDeviceChannelPageRequest;
import com.solo.gb28181.response.GbDeviceChannelResponse;
import com.solo.gb28181.response.GbDeviceChannelTreeNodeResponse;
import com.solo.gb28181.service.GbDeviceChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 国标设备通道管理
 * <p>通道入库由 Catalog 同步自动完成，此外提供页面手动增删改。</p>
 */
@RestController
@RequestMapping("/api/gb28181/device-channel")
@RequiredArgsConstructor
public class GbDeviceChannelController {

    private final GbDeviceChannelService channelService;

    /** 分页查询通道 */
    @PostMapping("/page")
    @RequirePermission({"gb28181:device-channel:list"})
    public Result<PageResult<GbDeviceChannelResponse>> page(@RequestBody GbDeviceChannelPageRequest request) {
        Page<GbDeviceChannelResponse> page = channelService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /** 按设备主键列出全部通道（设备详情页用） */
    @GetMapping("/by-device/{deviceDbId}")
    @RequirePermission({"gb28181:device-channel:list"})
    public Result<List<GbDeviceChannel>> listByDevice(@PathVariable Long deviceDbId) {
        List<GbDeviceChannel> list = channelService.lambdaQuery()
                .eq(GbDeviceChannel::getDeviceDbId, deviceDbId)
                .orderByAsc(GbDeviceChannel::getChannelId)
                .list();
        return Result.success(list);
    }

    /** 手动新增通道。状态与来源由后端强制设定，忽略入参。 */
    @PostMapping("/save")
    @RequirePermission({"gb28181:device-channel:add"})
    public Result<Void> save(@RequestBody GbDeviceChannel channel) {
        channelService.createByManual(channel);
        return Result.success();
    }

    /** 手动编辑通道资料。状态/来源/streamId 不受影响。 */
    @PostMapping("/update")
    @RequirePermission({"gb28181:device-channel:edit"})
    public Result<Void> update(@RequestBody GbDeviceChannel channel) {
        if (channel.getId() == null) {
            throw new ServiceException("通道ID不能为空");
        }
        channelService.updateByManual(channel);
        return Result.success();
    }

    /** 删除通道 */
    @DeleteMapping("/{id}")
    @RequirePermission({"gb28181:device-channel:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        channelService.deleteById(id);
        return Result.success();
    }

    /** 获取设备通道树（按设备 → 通道层级组装） */
    @GetMapping("/tree")
    @RequirePermission({"gb28181:device-channel:list"})
    public Result<List<GbDeviceChannelTreeNodeResponse>> tree() {
        return Result.success(channelService.getDeviceChannelTree());
    }
}
