package com.solo.media.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.media.entity.MediaServer;
import com.solo.media.service.MediaServerService;
import com.solo.onvif.entity.OnvifDevice;
import com.solo.onvif.service.OnvifDeviceService;
import com.solo.media.response.HomeOverviewResponse;
import com.solo.media.response.StreamStatsGbItem;
import com.solo.media.response.StreamStatsOnvifItem;
import com.solo.media.response.StreamStatsProxyItem;
import com.solo.media.response.StreamStatsPushItem;
import com.solo.media.response.StreamStatsSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页概览统计
 *
 * <p>面向所有登录用户的聚合只读指标，不挂按钮权限码——首页是登录后的默认落地页，
 * 任何账号看到的数据口径完全一致，不因角色权限不同而缺块。接口均为只读，不暴露管理能力。</p>
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

    /**
     * 首页流统计：媒体节点列表（只读，供首页节点切换，不含任何管理能力）
     */
    @Operation(summary = "首页-媒体节点列表")
    @GetMapping("/media-servers")
    public Result<List<MediaServer>> mediaServers() {
        return Result.success(mediaServerService.list());
    }

    /**
     * 首页流统计：节点 KPI 概要
     */
    @Operation(summary = "首页-节点流统计概要")
    @GetMapping("/media-servers/{serverId}/stream-stats/summary")
    public Result<StreamStatsSummaryResponse> streamStatsSummary(@PathVariable String serverId) {
        return Result.success(mediaServerService.getStreamStatsSummary(serverId));
    }

    /**
     * 首页流统计：按类型返回会话 / 流明细
     *
     * @param type gb=国标点播回放会话，push=第三方推流，proxy=拉流代理，onvif=ONVIF 会话
     */
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
