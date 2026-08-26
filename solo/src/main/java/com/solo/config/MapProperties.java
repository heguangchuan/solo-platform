package com.solo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 地图配置，对应 application.yaml 中 map.* 段。
 *
 * <p>前端首页电子地图（Leaflet + 高德瓦片）的默认视图、瓦片源、聚合行为等都从这里读。
 * 部署到不同区域的项目时改 application.yaml 即可，不用动前端代码或重新发布前端。</p>
 *
 * <p>字段命名走 Spring Boot RelaxedBinding：YAML 的 kebab-case
 * （如 {@code default-center-lng}）会自动绑定到 camelCase 字段（{@code defaultCenterLng}）。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "map")
public class MapProperties {

    /** 初始视图中心经度 */
    private Double defaultCenterLng;

    /** 初始视图中心纬度 */
    private Double defaultCenterLat;

    /** 初始缩放级别 */
    private Integer defaultZoom;

    /** 最小缩放 */
    private Integer minZoom;

    /** 最大缩放 */
    private Integer maxZoom;

    /** 瓦片源 URL 模板，含 {s}/{x}/{y}/{z} 占位 */
    private String tileUrl;

    /** 瓦片源子域（多服务器负载均衡用） */
    private String tileSubdomains;

    /** 缩放到此级别及以上不再聚合 */
    private Integer disableClusteringAtZoom;

    /** 视口变化 → 拉新 marker 的 debounce 延迟（ms） */
    private Integer fetchDebounceMs;

    /** 单次请求 marker 上限（与后端 SQL LIMIT 保持一致） */
    private Integer markerLimit;

    /**
     * 地图坐标系：WGS84 / GCJ02 / BD09。
     *
     * <p>与瓦片源对齐——瓦片是 GCJ-02（高德/腾讯/天地图）这里就配 GCJ02，
     * 瓦片是 BD-09（百度）这里就配 BD09。DB 始终存 WGS-84（设备原始上报），
     * {@code queryMapMarkers} 按这个值在出口处做转换，前端无脑用。</p>
     *
     * <p>代码默认 GCJ02，对应默认瓦片源（高德）。要切百度就把 yaml 的
     * {@code coord-system} 改成 BD09 + tile-url 切百度瓦片 URL（需配 BaiduCRS）。</p>
     */
    private String coordSystem = "GCJ02";
}
