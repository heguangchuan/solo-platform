package com.solo.dashboard.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 地图配置响应（Leaflet 地图初始化用）
 *
 * <p>对应 {@code GET /api/dashboard/map-config}。前端在首页 / 通道地图 / 选点对话框初始化时拉一次，
 * 缓存到内存里复用。字段缺失时返回 {@code null}，前端用兜底默认填充，这样 yaml 不填也能工作。</p>
 *
 * <p>字段名刻意使用全名（{@code centerLng} / {@code centerLat} 而不是简写）——配置类调一次就缓存，
 * 不在大列表里循环，体积不是问题，可读性优先。</p>
 */
@Schema(description = "地图配置响应（Leaflet 地图初始化用）")
@Data
public class MapConfigResponse {

    /** 初始中心经度（WGS84 坐标系下，除非 {@link #coordSystem} 指明其他） */
    @Schema(description = "初始中心经度（WGS84 坐标系下）")
    private Double centerLng;

    /** 初始中心纬度 */
    @Schema(description = "初始中心纬度")
    private Double centerLat;

    /** 初始缩放级别（典型 8~16） */
    @Schema(description = "初始缩放级别（典型 8~16）")
    private Integer zoom;

    /** 最小缩放（用户缩到不能再小） */
    @Schema(description = "最小缩放")
    private Integer minZoom;

    /** 最大缩放（用户放到不能再大） */
    @Schema(description = "最大缩放")
    private Integer maxZoom;

    /** 瓦片 URL 模板（Leaflet {@code L.tileLayer(url, options)} 第一参），按 {@link #tileMode} 二选一 */
    @Schema(description = "瓦片 URL 模板")
    private String tileUrl;

    /** 瓦片子域（用于多 CDN 负载均衡，例 {@code "abc"}；不需要时给空串） */
    @Schema(description = "瓦片子域（用于多 CDN 负载均衡）")
    private String tileSubdomains;

    /** 此缩放级别及以上不做 marker 聚合（典型 14~16） */
    @Schema(description = "此缩放级别及以上不做 marker 聚合（典型 14~16）")
    private Integer disableClusteringAtZoom;

    /** 视口变化后等待多少 ms 再发新数据请求（防抖） */
    @Schema(description = "视口变化后等待多少 ms 再发新数据请求（防抖）")
    private Integer fetchDebounceMs;

    /** 单次 marker 渲染上限（超过会聚合 / 抽样） */
    @Schema(description = "单次 marker 渲染上限（超过会聚合 / 抽样）")
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
    @Schema(description = "地图坐标系：WGS84 / GCJ02 / BD09")
    private String coordSystem;

    /**
     * 瓦片模式：offline / online（默认 offline）
     *
     * <p>纯 UI 元数据，<b>前端不应拿这个字段去拼 tileUrl</b>（{@link #tileUrl} 字段已经够用）。
     * 这里只给前端一个角标/提示用——比如离线模式下显示「⚠️ 离线模式，部分区域可能没瓦片」之类的提示。</p>
     */
    @Schema(description = "瓦片模式：offline / online（默认 offline）")
    private String tileMode;
}
