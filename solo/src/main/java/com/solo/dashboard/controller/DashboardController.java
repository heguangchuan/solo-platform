package com.solo.dashboard.controller;

import com.solo.common.Result;
import com.solo.config.MapProperties;
import com.solo.dashboard.response.MapConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页 / 地图选点对话框的公共配置
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code GET /api/dashboard/map-config} — 地图配置（Leaflet 初始化时拉一次）</li>
 * </ul>
 *
 * <p>说明：原 {@code /api/dashboard/stats} 聚合统计接口已废弃（前端未消费），
 * 当前只保留地图配置一项。</p>
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final MapProperties mapProperties;

    /**
     * 地图配置（Leaflet 地图组件初始化时调一次）
     *
     * <p>响应字段全部来自 {@code application.yaml} 的 {@code map.*} 段。
     * 字段缺失时返回 {@code null}，前端用兜底默认填充——这样 yaml 不填也能正常工作。</p>
     *
     * <p>{@link MapConfigResponse#getTileUrl()} 的取值规则：</p>
     * <ul>
     *   <li>{@code map.tile-mode=online} → 用 {@code map.tile-url-online}（外网瓦片）</li>
     *   <li>其他（含 {@code offline}）→ 用 {@code map.tile-url-offline}（本地瓦片）</li>
     * </ul>
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
