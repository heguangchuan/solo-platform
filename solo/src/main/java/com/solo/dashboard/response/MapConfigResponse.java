package com.solo.dashboard.response;

import lombok.Data;

/**
 * 地图配置响应
 *
 * <p>对应 {@link com.solo.config.MapProperties}，由 {@code DashboardController#mapConfig} 返回。
 * 前端 Leaflet 地图在初始化时拉一次，字段缺失时用前端兜底默认。</p>
 *
 * <p>字段名刻意使用全名（centerLng / centerLat 而不是简写），因为这个接口是配置类，
 * 调一次缓存到内存，不在大列表里循环使用，体积不是问题，可读性优先。</p>
 */
@Data
public class MapConfigResponse {

    /** 初始中心经度 */
    private Double centerLng;

    /** 初始中心纬度 */
    private Double centerLat;

    /** 初始缩放级别 */
    private Integer zoom;

    /** 最小缩放 */
    private Integer minZoom;

    /** 最大缩放 */
    private Integer maxZoom;

    /** 瓦片 URL 模板 */
    private String tileUrl;

    /** 瓦片子域 */
    private String tileSubdomains;

    /** 此级别及以上不聚合 */
    private Integer disableClusteringAtZoom;

    /** 视口变化 debounce（ms） */
    private Integer fetchDebounceMs;

    /** marker 单次上限 */
    private Integer markerLimit;

    /**
     * 地图坐标系：WGS84 / GCJ02 / BD09
     *
     * <p>前端用这个值做两件事：
     * <ol>
     *   <li>选 Leaflet 的 CRS（BD-09 → 用 {@code L.CRS.Baidu}；其他 → 默认 {@code L.CRS.EPSG3857}）</li>
     *   <li>判断中心点是否需要做坐标转换（一般中心点直接用就行，因为 yaml 配的就是这个系）</li>
     * </ol>
     */
    private String coordSystem;

    /**
     * 瓦片模式：offline / online（默认 offline）
     *
     * <p>纯 UI 元数据，<b>前端不应拿这个字段去拼 tileUrl</b>（{@link #tileUrl} 字段已经够用）。
     * 这里只给前端一个角标/提示用——比如离线模式下显示「⚠️ 离线模式，部分区域可能没瓦片」之类的提示。</p>
     */
    private String tileMode;
}
