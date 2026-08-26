package com.solo.dashboard.controller;

import com.solo.common.Result;
import com.solo.config.MapProperties;
import com.solo.dashboard.response.DashboardStatsResponse;
import com.solo.dashboard.response.MapConfigResponse;
import com.solo.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页聚合统计控制器
 *
 * <p>一次 GET 拉完全部首页数据，避免前端瀑布请求。
 * 接口本身不鉴权——只要登录的用户都能看（首页对所有角色可见）。</p>
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final MapProperties mapProperties;

    @GetMapping("/stats")
    public Result<DashboardStatsResponse> stats() {
        return Result.success(dashboardService.aggregate());
    }

    /**
     * 地图配置：前端 Leaflet 地图组件初始化时拉一次（{@code GET /api/dashboard/map-config}）。
     * <p>所有字段从 {@link MapProperties}（对应 application.yaml 的 {@code map.*} 段）转出，
     * 字段缺失时返回 null，由前端用兜底默认填充。这样 YAML 不填也能工作。</p>
     */
    @GetMapping("/map-config")
    public Result<MapConfigResponse> mapConfig() {
        MapConfigResponse r = new MapConfigResponse();
        r.setCenterLng(mapProperties.getDefaultCenterLng());
        r.setCenterLat(mapProperties.getDefaultCenterLat());
        r.setZoom(mapProperties.getDefaultZoom());
        r.setMinZoom(mapProperties.getMinZoom());
        r.setMaxZoom(mapProperties.getMaxZoom());
        r.setTileUrl(mapProperties.getTileUrl());
        r.setTileSubdomains(mapProperties.getTileSubdomains());
        r.setDisableClusteringAtZoom(mapProperties.getDisableClusteringAtZoom());
        r.setFetchDebounceMs(mapProperties.getFetchDebounceMs());
        r.setMarkerLimit(mapProperties.getMarkerLimit());
        r.setCoordSystem(mapProperties.getCoordSystem());
        return Result.success(r);
    }
}
