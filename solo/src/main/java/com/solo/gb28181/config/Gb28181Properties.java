package com.solo.gb28181.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * GB/T 28181 国标平台配置
 * 对应 application.yaml 中 gb28181 段
 */
@Data
@Component
@ConfigurationProperties(prefix = "gb28181")
public class Gb28181Properties {

    /**
     * 是否启用国标服务
     */
    private boolean enabled = false;

    /**
     * SIP 监听 IP；留空表示监听所有网卡
     */
    private String ip;

    /**
     * SIP 监听端口
     */
    private int port = 5060;

    /**
     * 本平台 SIP ID（20 位）
     */
    private String id;

    /**
     * 本平台 SIP 域
     */
    private String domain;

    /**
     * 设备注册鉴权密码
     */
    private String password;

    /**
     * 设备注册有效期（秒）
     */
    private int registerExpires = 3600;

    /**
     * 心跳超时倍数（multiplier × Keepalive 周期未收到心跳即判定离线）
     */
    private int keepaliveTimeoutMultiplier = 3;

    /**
     * 是否打印 SIP 协议栈收发报文日志（开启会非常啰嗦，仅在调试设备方言时启用）
     */
    private boolean sipLog = false;

    /**
     * 是否优先使用 SIP 报文的源 IP/端口作为设备地址。
     * <p>true ：直接取 UDP/TCP 收包源（适合大多数 NAT 后设备）</p>
     * <p>false：优先取 Via 头的 received/rport（适合公网直连且要求严格按 Via 路由的场景）</p>
     */
    private boolean useSourceIpAsRemoteAddress = true;

    /**
     * 业务线程池配置 - 核心线程数（默认：max(2 * CPU, 16)）
     */
    private Integer corePoolSize;

    /**
     * 业务线程池配置 - 最大线程数（默认：10 * corePoolSize）
     */
    private Integer maxPoolSize;

    /**
     * 业务线程池配置 - 队列容量（默认：10000）
     */
    private Integer queueCapacity;

    /**
     * 默认资源目录分组。启动期 {@code GbGroupBootstrap} 按此 upsert 一个业务分组(215)，
     * 通道注册/导入/新增未指定分组时统一挂到该分组下。
     */
    private DefaultGroup defaultGroup = new DefaultGroup();

    /**
     * 默认资源目录分组配置。
     * <p>{@link #id} 是默认分组的固定主键 {@code 0000}（其余分组主键走雪花ID），作为 {@code gb_device_channel.group_id} 的兜底值；
     * {@link #groupId} 是 20 位国标业务分组码（第 11-13 位应为 215），拼 Catalog 上报上级时作目录节点 ID，
     * 因此必须是合法 20 位 ID；{@link #name} 为目录显示名称。</p>
     */
    @Data
    public static class DefaultGroup {

        /**
         * 默认分组固定主键（gb_group.id = 通道 group_id 兜底值；仅默认分组用 0000）
         */
        private String id = "0000";

        /**
         * 默认分组国标 ID（20 位，215 业务分组码），拼 Catalog 上报上级用
         */
        private String groupId = "60000000042150000001";

        /**
         * 默认分组名称
         */
        private String name = "默认目录";
    }
}
