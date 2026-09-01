package com.solo.dashboard.controller;

import com.solo.common.Result;
import com.solo.config.MapProperties;
import com.solo.dashboard.response.MapConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 地图配置控制器（首页 / 通道地图选点对话框共用）
 *
 * <p>仅暴露 {@code GET /api/dashboard/map-config}。原 {@code /api/dashboard/stats} 聚合统计
 * 已废弃——前端未消费，仅留地图配置一项。</p>
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final MapProperties mapProperties;

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
        // 按 tile-mode 二选一：offline 用本地瓦片路径，online 用外网 URL
        String mode = mapProperties.getTileMode();
        String tileUrl = "online".equalsIgnoreCase(mode)
                ? mapProperties.getTileUrlOnline()
                : mapProperties.getTileUrlOffline();
        r.setTileUrl(tileUrl);
        r.setTileSubdomains(mapProperties.getTileSubdomains());
        r.setDisableClusteringAtZoom(mapProperties.getDisableClusteringAtZoom());
        r.setFetchDebounceMs(mapProperties.getFetchDebounceMs());
        r.setMarkerLimit(mapProperties.getMarkerLimit());
        r.setCoordSystem(mapProperties.getCoordSystem());
        r.setTileMode(mode);
        return Result.success(r);
    }
}
