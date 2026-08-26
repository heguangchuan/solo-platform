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

    /**
     * 瓦片拉取模式：offline / online（默认 offline）。
     *
     * <p>纯二选一，不要混合：
     * <ul>
     *   <li>{@code offline}：瓦片文件放前端项目 {@code solo-ui/public/tiles/{z}/{x}/{y}.png}，
     *       Vite dev 从 public/ 静态服务，nginx prod 同路径静态服务。
     *       客户端请求的 {@code tileUrl} 是 {@link #tileUrlOffline}（如 {@code /tiles/{z}/{x}/{y}.png}）。</li>
     *   <li>{@code online}：客户端直接走外网瓦片源，{@code tileUrl} 是 {@link #tileUrlOnline}
     *       （如 {@code https://webrd01.is.autonavi.com/...}）。需要客户端能访问外网（CORS 走不通的话
     *       自己加 nginx / Vite proxy）。</li>
     * </ul>
     *
     * <p>前端只管用 {@code tileUrl} 渲染，不关心是哪个模式。模式字段只用于 UI 角标（"离线模式/在线模式"）。</p>
     */
    private String tileMode = "offline";

    /**
     * 离线模式的瓦片 URL（前端地图组件用）。比如 {@code /tiles/{z}/{x}/{y}.png}，
     * Vite dev / nginx prod 配好 static 目录服务即可（Vite 自动从 public/ 取，nginx 配 root）。
     */
    private String tileUrlOffline;

    /**
     * 在线模式的瓦片 URL（前端地图组件用）。外网瓦片源完整 URL 模板，比如：
     * <ul>
     *   <li>高德：{@code https://webrd01.is.autonavi.com/appmaptile?x={x}&y={y}&z={z}&lang=zh_cn&size=1&scale=1&style=8}</li>
     *   <li>百度：{@code https://maponline0.map.bdimg.com/tile/?qt=vtile&x={x}&y={y}&z={z}&styles=pl}</li>
     *   <li>天地图：{@code https://t0.tianditu.gov.cn/vec_w/wmts?...}</li>
     * </ul>
     * 客户端直接请求（无 Vite/nginx 代理），CORS 走不通的话自己加 proxy。
     */
    private String tileUrlOnline;
}
