package com.solo.media.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.media.entity.MediaServer;
import com.solo.media.response.HomeOverviewResponse;
import com.solo.media.response.StreamStatsSummaryResponse;
import com.solo.media.service.MediaServerService;
import com.solo.onvif.entity.OnvifDevice;
import com.solo.onvif.service.OnvifDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@Tag(name = "首页概览", description = "首页概览统计")
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final GbDeviceService gbDeviceService;
    private final GbDeviceChannelService gbDeviceChannelService;
    private final OnvifDeviceService onvifDeviceService;
    private final MediaServerService mediaServerService;

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

    @Operation(summary = "首页-媒体节点列表")
    @GetMapping("/media-servers")
    public Result<List<MediaServer>> mediaServers() {
        return Result.success(mediaServerService.list());
    }

    @Operation(summary = "首页-节点流统计概要")
    @GetMapping("/media-servers/{serverId}/stream-stats/summary")
    public Result<StreamStatsSummaryResponse> streamStatsSummary(@PathVariable String serverId) {
        return Result.success(mediaServerService.getStreamStatsSummary(serverId));
    }

    @Operation(summary = "首页-节点流统计明细")
    @GetMapping("/media-servers/{serverId}/stream-stats/{type}")
    public Result<List<?>> streamStatsDetail(@PathVariable String serverId, @PathVariable String type) {
        return switch (type) {
            case "gb" -> Result.success(mediaServerService.getStreamStatsGbDetail(serverId));
            case "push" -> Result.success(mediaServerService.getStreamStatsPushDetail(serverId));
            case "proxy" -> Result.success(mediaServerService.getStreamStatsProxyDetail(serverId));
            case "onvif" -> Result.success(mediaServerService.getStreamStatsOnvifDetail(serverId));
            default -> throw new ServiceException("不支持的流统计类型: " + type);
        };
    }
}
