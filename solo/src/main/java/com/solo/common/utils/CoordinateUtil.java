package com.solo.common.utils;

import lombok.experimental.UtilityClass;

/**
 * 坐标系转换工具：WGS-84 / GCJ-02 / BD-09
 *
 * <ul>
 *   <li>WGS-84：国际标准，GPS 设备原始坐标，GB/T 28181 设备一般用这个</li>
 *   <li>GCJ-02：火星坐标（国测局加密），高德 / 腾讯 / 阿里系地图瓦片用这个</li>
 *   <li>BD-09：百度坐标（在 GCJ-02 基础上再加密），百度地图瓦片用这个</li>
 * </ul>
 *
 * <p>转换有损：WGS-84 ↔ GCJ-02 互转有累计误差，国内几十厘米 ~ 几米，境外无偏移（直接原值返回）。
 * GCJ-02 ↔ BD-09 互转无损。</p>
 *
 * <p>具体页面决策见 {@code application.yaml} 的 {@code map.coord-system}：哪个坐标系就和哪个瓦片源对齐。
 * DB 统一存 WGS-84（设备原始上报），{@code queryMapMarkers} 出口时按配置转成对应瓦片系，
 * 前端无脑用，不在 marker render 时再做转换。</p>
 */
@UtilityClass
public class CoordinateUtil {

    /** BD-09 二次加密用的 X 轴 π 常量 */
    private static final double X_PI = Math.PI * 3000.0 / 180.0;

    private static final double PI = Math.PI;

    /** 克拉索夫斯基椭球长半轴（GCJ-02 加密用） */
    private static final double A = 6378245.0;

    /** 克拉索夫斯基椭球偏心率平方 */
    private static final double EE = 0.00669342162296594323;

    /**
     * 中国大陆粗略矩形框。境外坐标不做偏移，直接原值返回。
     */
    public static boolean outOfChina(double lng, double lat) {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    private static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y
                + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y
                + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320.0 * Math.sin(y / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }

    /**
     * WGS-84 → GCJ-02
     */
    public static double[] wgs84ToGcj02(double lng, double lat) {
        if (outOfChina(lng, lat)) {
            return new double[]{lng, lat};
        }
        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dLng = transformLng(lng - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new double[]{lng + dLng, lat + dLat};
    }

    /**
     * GCJ-02 → WGS-84（反算，迭代一次逼近；国内误差 ~ 1m）。
     * 用闭式反推：先粗算一次 WGS-84（用 GCJ-02 当 WGS-84 喂回去算偏移量），再用 GCJ-02 - 偏移量 = WGS-84。
     */
    public static double[] gcj02ToWgs84(double lng, double lat) {
        if (outOfChina(lng, lat)) {
            return new double[]{lng, lat};
        }
        double[] approx = wgs84ToGcj02(lng, lat);
        double dLng = approx[0] - lng;
        double dLat = approx[1] - lat;
        return new double[]{lng - dLng, lat - dLat};
    }

    /**
     * GCJ-02 → BD-09（无损）
     */
    public static double[] gcj02ToBd09(double lng, double lat) {
        double z = Math.sqrt(lng * lng + lat * lat) + 0.00002 * Math.sin(lat * X_PI);
        double theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng * X_PI);
        return new double[]{z * Math.cos(theta) + 0.0065, z * Math.sin(theta) + 0.006};
    }

    /**
     * BD-09 → GCJ-02（无损）
     */
    public static double[] bd09ToGcj02(double lng, double lat) {
        double x = lng - 0.0065;
        double y = lat - 0.006;
        double z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * X_PI);
        double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI);
        return new double[]{z * Math.cos(theta), z * Math.sin(theta)};
    }

    /**
     * WGS-84 → BD-09（组合两次）
     */
    public static double[] wgs84ToBd09(double lng, double lat) {
        if (outOfChina(lng, lat)) {
            return new double[]{lng, lat};
        }
        double[] gcj = wgs84ToGcj02(lng, lat);
        return gcj02ToBd09(gcj[0], gcj[1]);
    }

    /**
     * BD-09 → WGS-84（组合两次）
     */
    public static double[] bd09ToWgs84(double lng, double lat) {
        if (outOfChina(lng, lat)) {
            return new double[]{lng, lat};
        }
        double[] gcj = bd09ToGcj02(lng, lat);
        return gcj02ToWgs84(gcj[0], gcj[1]);
    }
}
