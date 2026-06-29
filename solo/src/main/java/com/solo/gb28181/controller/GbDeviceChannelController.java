package com.solo.gb28181.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.GbDeviceChannelPageRequest;
import com.solo.gb28181.service.GbDeviceChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 国标设备通道查询
 * <p>通道入库由 Catalog 同步自动完成，这里只提供查询。</p>
 */
@RestController
@RequestMapping("/api/gb28181/device-channel")
@RequiredArgsConstructor
public class GbDeviceChannelController {

    private final GbDeviceChannelService channelService;

    /** 分页查询通道 */
    @PostMapping("/page")
    @RequirePermission({"gb28181:device-channel:list"})
    public Result<PageResult<GbDeviceChannel>> page(@RequestBody GbDeviceChannelPageRequest request) {
        Page<GbDeviceChannel> page = channelService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /** 按设备主键列出全部通道（设备详情页用） */
    @GetMapping("/by-device/{deviceDbId}")
    @RequirePermission({"gb28181:device-channel:list"})
    public Result<List<GbDeviceChannel>> listByDevice(@PathVariable Long deviceDbId) {
        return Result.success(channelService.listByDeviceDbId(deviceDbId));
    }
}
