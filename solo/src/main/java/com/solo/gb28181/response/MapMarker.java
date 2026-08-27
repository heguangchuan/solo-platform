package com.solo.gb28181.response;

import lombok.Data;

/**
 * 单点 marker（首页地图打点用）。
 *
 * <p>字段跟 {@link com.solo.gb28181.entity.GbDeviceChannel} 对齐——
 * longitude/latitude 跟瓦片源（map-config.coordSystem）同系，给 marker 摆位用；
 * longitude/latitude 后面三套冗余变体（WGS-84 / GCJ-02 / BD-09）跟通道管理列表保持一致，
 * popup 显示三套坐标值给运维。</p>
 *
 * <p>抽出为顶层类（而非 {@code MapMarkerResponse} 的内部类），避免 MyBatis 用 {@code $MapMarker} 解析内部类时
 * 不同 MyBatis 版本的兼容问题。</p>
 */
@Data
public class MapMarker {

    /** channelId（GB 码） */
    private String channelId;

    /** deviceId（所属设备 GB 码） */
    private String deviceId;

    /** 通道名称 */
    private String name;

    /** 经度（target 系，对应瓦片源） */
    private Double longitude;

    /** 纬度（target 系） */
    private Double latitude;

    /** WGS-84 经度（DB canonical，NOT NULL——SQL WHERE 已过滤 null/0） */
    private Double longitudeWgs84;

    /** WGS-84 纬度 */
    private Double latitudeWgs84;

    /** GCJ-02 经度（高德瓦片用，可能 null——历史数据 / populateCoordVariants 漏算） */
    private Double longitudeGcj02;

    /** GCJ-02 纬度 */
    private Double latitudeGcj02;

    /** BD-09 经度（百度瓦片用，可能 null） */
    private Double longitudeBd09;

    /** BD-09 纬度 */
    private Double latitudeBd09;

    /** 状态：ON 在线 / OFF 离线 / UNREGISTERED 未注册 */
    private String status;
}
