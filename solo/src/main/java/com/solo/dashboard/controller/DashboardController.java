package com.solo.dashboard.controller;

import com.solo.common.Result;
import com.solo.config.MapProperties;
import com.solo.dashboard.response.MapConfigResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页地图配置管理
 */
@Tag(name = "首页地图配置管理", description = "首页地图配置管理")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final MapProperties mapProperties;

    /**
     * 地图配置
     */
    @Operation(summary = "地图配置", description = "首页 / 地图选点对话框初始化用")
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
