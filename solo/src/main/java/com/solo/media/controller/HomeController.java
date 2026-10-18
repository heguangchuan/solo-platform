package com.solo.media.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.common.Result;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.media.entity.MediaServer;
import com.solo.media.service.MediaServerService;
import com.solo.onvif.entity.OnvifDevice;
import com.solo.onvif.service.OnvifDeviceService;
import com.solo.media.response.HomeOverviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页概览统计
 *
 * <p>面向所有登录用户的聚合只读指标（纯 count 查询，无明细），
 * 因此不挂按钮权限码——首页是登录后的默认落地页，无权用户也不应看到空白。</p>
 */
@Tag(name = "首页概览", description = "首页概览统计")
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final GbDeviceService gbDeviceService;
    private final GbDeviceChannelService gbDeviceChannelService;
    private final OnvifDeviceService onvifDeviceService;
    private final MediaServerService mediaServerService;

    /**
     * 首页概览统计：国标设备 / 国标通道 / ONVIF 设备 / 媒体节点的总数与在线数
     */
    @Operation(summary = "首页概览统计")
    @GetMapping("/overview")
    public Result<HomeOverviewResponse> overview() {
        HomeOverviewResponse r = new HomeOverviewResponse();
        r.setGbDeviceTotal(gbDeviceService.count());
        r.setGbDeviceOnline(gbDeviceService.count(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getStatus, 1)));
        r.setGbChannelTotal(gbDeviceChannelService.count());
        r.setGbChannelOnline(gbDeviceChannelService.count(
                new LambdaQueryWrapper<GbDeviceChannel>().eq(GbDeviceChannel::getStatus, "ON")));
        r.setOnvifDeviceTotal(onvifDeviceService.count());
        r.setOnvifDeviceOnline(onvifDeviceService.count(
                new LambdaQueryWrapper<OnvifDevice>().eq(OnvifDevice::getStatus, 1)));
        r.setMediaServerTotal(mediaServerService.count());
        r.setMediaServerOnline(mediaServerService.count(
                new LambdaQueryWrapper<MediaServer>().eq(MediaServer::getStatus, 1)));
        return Result.success(r);
    }
}
