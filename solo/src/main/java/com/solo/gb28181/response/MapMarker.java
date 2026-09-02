package com.solo.gb28181.response;

import lombok.Data;

/**
 * 单点 marker（首页地图打点用）
 *
 * <p>对应 {@code GET /api/gb28181/device-channel/map-markers} 返回的 {@code markers[]} 元素。</p>
 *
 * <p>{@link #longitude} / {@link #latitude} 跟瓦片源（{@code map-config.coordSystem}）同系，给 marker 摆位用；
 * 后三套冗余变体（WGS-84 / GCJ-02 / BD-09）跟通道管理列表保持一致，popup 显示三套坐标值给运维。</p>
 */
@Data
public class MapMarker {

    /** 通道国标 ID */
    private String channelId;

    /** 所属设备国标 ID */
    private String deviceId;

    /** 通道名称 */
    private String name;

    /** 经度（与瓦片源同系，用于 marker 摆位） */
    private Double longitude;

    /** 纬度（与瓦片源同系） */
    private Double latitude;

    /** WGS-84 经度（原始坐标；通道入库时已校验非空） */
    private Double longitudeWgs84;

    /** WGS-84 纬度 */
    private Double latitudeWgs84;

    /** GCJ-02 经度（高德瓦片用；可能为 null——历史数据漏算） */
    private Double longitudeGcj02;

    /** GCJ-02 纬度 */
    private Double latitudeGcj02;

    /** BD-09 经度（百度瓦片用；可能为 null） */
    private Double longitudeBd09;

    /** BD-09 纬度 */
    private Double latitudeBd09;

    /** 状态：{@code ON} 在线 / {@code OFF} 离线 / {@code UNREGISTERED} 未注册 */
    private String status;
}
