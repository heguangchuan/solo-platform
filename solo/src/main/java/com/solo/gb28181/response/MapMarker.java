package com.solo.gb28181.response;

import lombok.Data;

/**
 * 单点 marker（紧凑字段：c=channelId, d=deviceId, n=name, x=longitude, y=latitude, s=status）
 *
 * <p>字段名刻意压成单字符以最小化 JSON 体积。5000 条 × 每条 ~50B ≈ 250KB，gzip 后 ~50KB。
 * 抽出为顶层类（而非 {@code MapMarkerResponse} 的内部类），避免 MyBatis 用 {@code $MapMarker} 解析内部类时
 * 不同 MyBatis 版本的兼容问题。</p>
 */
@Data
public class MapMarker {

    /** channelId（GB 码） */
    private String c;

    /** deviceId（所属设备 GB 码） */
    private String d;

    /** 通道名称 */
    private String n;

    /** 经度 */
    private double x;

    /** 纬度 */
    private double y;

    /** 状态简码：1=ON 在线 / 0=OFF 离线 / -1=UNREGISTERED 未注册 */
    private int s;
}
