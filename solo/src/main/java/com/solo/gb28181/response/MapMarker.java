package com.solo.gb28181.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 单点 marker（首页地图打点用）
 *
 * <p>对应 {@code GET /api/gb28181/device-channel/map-markers} 返回的 {@code markers[]} 元素。</p>
 *
 * <p>{@link #longitude} / {@link #latitude} 跟瓦片源（{@code map-config.coordSystem}）同系，给 marker 摆位用；
 * 后三套冗余变体（WGS-84 / GCJ-02 / BD-09）跟通道管理列表保持一致，popup 显示三套坐标值给运维。</p>
 */
@Schema(description = "地图打点 marker")
@Data
public class MapMarker {

    @Schema(description = "通道国标 ID")
    private String channelId;

    @Schema(description = "所属设备国标 ID")
    private String deviceId;

    @Schema(description = "通道名称")
    private String name;

    @Schema(description = "经度（与瓦片源同系，用于 marker 摆位）")
    private Double longitude;

    @Schema(description = "纬度（与瓦片源同系）")
    private Double latitude;

    @Schema(description = "WGS-84 经度（原始坐标）")
    private Double longitudeWgs84;

    @Schema(description = "WGS-84 纬度")
    private Double latitudeWgs84;

    @Schema(description = "GCJ-02 经度（高德瓦片用；可能为 null）")
    private Double longitudeGcj02;

    @Schema(description = "GCJ-02 纬度")
    private Double latitudeGcj02;

    @Schema(description = "BD-09 经度（百度瓦片用；可能为 null）")
    private Double longitudeBd09;

    @Schema(description = "BD-09 纬度")
    private Double latitudeBd09;

    @Schema(description = "状态：ON 在线 / OFF 离线 / UNREGISTERED 未注册")
    private String status;
}
