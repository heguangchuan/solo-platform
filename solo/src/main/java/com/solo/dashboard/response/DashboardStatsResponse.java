package com.solo.dashboard.response;

import lombok.Data;

import java.util.List;

/**
 * 首页聚合统计数据响应体
 *
 * <p>一次 GET 把首页需要的全部数据带回来，避免前端 6+ 次瀑布请求。
 * 数据来源在 {@code DashboardService} 中聚合：MySQL 查设备/通道/节点、Redis 查负载与
 * 24h 趋势桶、{@code sys_message} 查最近告警。</p>
 */
@Data
public class DashboardStatsResponse {

    /**
     * 个性化问候
     */
    private Greeting greeting;

    /**
     * 国标设备聚合
     */
    private Device device;

    /**
     * 通道聚合
     */
    private Channel channel;

    /**
     * ZLM 节点列表（含在线状态 + 负载）
     */
    private List<MediaServer> mediaServer;

    /**
     * 流聚合
     */
    private Stream stream;

    /**
     * 告警聚合
     */
    private Alert alert;

    /**
     * 24h 趋势（每小时一个点）
     */
    private Trends trends;

    /**
     * 厂商分布（按 device.manufacturer 聚合）
     */
    private List<Distribution> manufacturerDist;

    /**
     * 较 1h 前的差值（KPI 卡上的"+"-"箭头用）
     */
    private CompareHour compareHour;

    @Data
    public static class Greeting {
        private String username;
        private Integer hour;
    }

    @Data
    public static class Device {
        private Long total;
        private Long online;
        private Long offline;
        private Long unregistered;
        /** 0~1，前端可直接乘 100 渲染百分比 */
        private Double onlineRate;
    }

    @Data
    public static class Channel {
        private Long total;
        private Long online;
    }

    @Data
    public static class MediaServer {
        private String serverId;
        private String name;
        private Integer status;
        private Double loadScore;
        private String hostIp;
        private Integer httpPort;
    }

    @Data
    public static class Stream {
        private Long live;
        private Long push;
        private Long proxy;
    }

    @Data
    public static class Alert {
        private Long unreadCount;
        private Long todayCount;
        private List<Recent> recent;
    }

    @Data
    public static class Recent {
        private Long id;
        private String title;
        private String level;
        private String createTime;
        private String route;
    }

    @Data
    public static class Trends {
        private List<TrendPoint> deviceOnline24h;
        private List<TrendPoint> activeStream24h;
    }

    @Data
    public static class TrendPoint {
        /** 形如 "14:00" */
        private String hour;
        private Long value;
    }

    @Data
    public static class Distribution {
        private String name;
        private Long value;
    }

    @Data
    public static class CompareHour {
        /** 正数=增加，负数=减少；null=无 1h 前数据无法比较 */
        private Long deviceOnline;
        private Long activeStream;
        private Long alertCount;
    }
}
