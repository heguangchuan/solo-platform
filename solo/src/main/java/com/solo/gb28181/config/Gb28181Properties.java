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
     * 手动存活探测超时（毫秒）。向设备发 DeviceInfo 查询后等待应答的时长，
     * 超时无应答判定离线。NAT 后 / 广域网 / 老旧设备响应慢，不宜过短。
     */
    private long detectTimeoutMs = 10000;

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
     * 级联下级侧配置（本平台作为下级向上级平台推送数据时使用）。
     */
    private Cascade cascade = new Cascade();

    /**
     * Catalog 分片累积调优（下级/平台设备的 Catalog 响应处理用）。
     * <p>设备通告的 SumNum 在协议层无"完成"信号，平台只能靠启发式判断何时收尾：
     * 静默超时（无新 item）→ partial finalize，保留已收数据；完全无响应（expire）→ 强制收尾，
     * 防止内存堆积。这两个阈值在大目录（4w+ 条）场景下需要放宽。</p>
     */
    private Catalog catalog = new Catalog();

    /**
     * 通道目录同步专用线程池调优（落库任务，IO 密集）。
     * <p>独立于业务线程池：Catalog 同步是 10w+ 通道下的重活，不能与 SIP 消息分发挤同一个池。</p>
     */
    private CatalogSyncPool catalogSyncPool = new CatalogSyncPool();

    /**
     * 默认资源目录分组配置。
     * <p>{@link #id} 是默认分组的固定主键 {@code 0000}（其余分组主键走雪花ID），作为 {@code gb_device_channel.group_id} 的兜底值；
     * {@link #groupId} 是 20 位国标业务分组码（第 11-13 位应为 215），拼 Catalog 上报上级时作目录节点 ID，
     * 因此必须是合法 20 位 ID；{@link #name} 为目录显示名称。</p>
     */
    @Data
    public static class DefaultGroup {

        /**
         * 默认分组固定主键（
         */
        private Long id ;

        /**
         * 默认分组国标 ID（20 位，215 业务分组码），拼 Catalog 上报上级用
         */
        private String groupId = "60000000042150000001";

        /**
         * 默认分组名称
         */
        private String name = "默认目录";
    }

    /**
     * 级联下级侧配置。
     * <p>本平台作为下级向上级平台推送数据（Catalog 响应、Notify 变更）时的分片/节奏控制。</p>
     */
    @Data
    public static class Cascade {

        /**
         * Catalog 响应分片大小：每包 XML 中 &lt;Item&gt; 数量上限。
         * <p>过大易触发上级 SIP MSG 长度限制或 UDP 分片，过小徒增交互次数；WVP 默认 3-5，本项目取 3。</p>
         */
        private int catalogPageSize = 3;

        /**
         * Catalog 响应分片间隔（毫秒）：连续两片 MESSAGE 之间的睡眠时间。
         * <p>UDP 突发发送易被上级丢包或触发 fair-queuing，间隔 50ms 足够宽松且总耗时可控。</p>
         */
        private long catalogPageIntervalMs = 50;

        /**
         * 上级点播（sendRtp）本地发送端口段起始。
         * <p>本平台把流转推给上级时，ZLM 从此段选一个本地端口发流。需与 ZLM 收流端口段错开，并在防火墙放行。</p>
         */
        private int sendRtpPortStart = 50000;

        /**
         * 上级点播（sendRtp）本地发送端口段结束。
         */
        private int sendRtpPortEnd = 60000;

        /**
         * 上级点播回 200 OK 后等待上级 ACK 的超时（毫秒）。超时未收到 ACK 即释放会话并向上级发 BYE。
         */
        private long sendAckTimeoutMs = 60_000;
    }

    /**
     * Catalog 分片累积调优。
     * <p>设备响应 Catalog 时按 SumNum 分多个 MESSAGE 报文分批送回，平台无法从协议层判定"已发完"，
     * 这两个阈值用来兜底：</p>
     * <ul>
     *   <li>{@link #idleFinalizeMs}：连续多少毫秒无新 item 进入累加 → 视为设备发完，已有数据 partial 落库</li>
     *   <li>{@link #expireMs}：完全多少毫秒无任何响应（含空响应）→ 强制收尾，防止 4w+ 大目录慢流时内存堆积</li>
     * </ul>
     * <p>默认值按 4w+ 大目录场景调宽：120s / 600s。普通小目录通常 10s 内收齐，根本走不到这些路径。</p>
     */
    @Data
    public static class Catalog {

        /**
         * 静默超时：连续多少毫秒无新 item → 视为设备发完，partial finalize。
         * <p>设备发完不发结束信号（GB28181 协议没这个字段），本参数用"长时间没新 item"做兜底。</p>
         */
        private long idleFinalizeMs = 120_000L;

        /**
         * 强制收尾：完全多少毫秒无任何响应 → sweep 路径 partial finalize，释放内存。
         * <p>兜底 idleFinalizeMs 失败的情况（如设备发了 item 后彻底失联但 lastNonEmptyUpdateMs 没刷）。</p>
         */
        private long expireMs = 600_000L;

        /**
         * 滚动缓冲阈值：分片累积达到该条数后立即触发批量入库，未必等齐 SumNum。
         * <p>GB28181 协议没有"发完"信号；靠 SumNum 全量收齐可能慢达数分钟，
         * 改为边收边落：每凑够此数就 flush 一次，最后一片连同残片一起收尾。
         * 调大 → 入库更少但内存占用更高；调小 → 入库更频繁但 DB 压力更大。默认 1000。</p>
         */
        private int flushThreshold = 1000;

        /**
         * 单批入库 SQL 的行数上限。MySQL 单条 INSERT 行数受 max_allowed_packet 限制，
         * 500-1000 行是安全区间。flush 缓冲 ≥ 该值时分片入库，每片独立执行 upsert SQL。</p>
         */
        private int batchSize = 500;

        /**
         * 丢包容忍：只有累计收到 ≥ sumNum * 阈值比例时，才删"已不存在的通道"（清空设备目录）；
         * 低于此比例说明 UDP 丢包或中途超时，保留已入库数据等下次 sync 补齐。
         * <p>默认 0.95：5% 缺失容忍。</p>
         */
        private double completenessThreshold = 0.95;
    }

    /**
     * Catalog 同步落库线程池配置。
     * <p>调优思路：DB 写并发受 MySQL 连接池（默认 10）和单行锁竞争约束，
     * 线程数过多只会增加上下文切换。core 20 / max 50 / queue 5000 适合单库 10w+ 通道批写；
     * 撑满后 CallerRunsPolicy 让上游（CatalogDataCache flush 线程）回压，自然限流。</p>
     */
    @Data
    public static class CatalogSyncPool {

        /** 核心线程数 */
        private int corePoolSize = 20;

        /** 最大线程数 */
        private int maxPoolSize = 50;

        /** 队列容量（每个任务是一次 flush 批次，500-1000 条） */
        private int queueCapacity = 5000;
    }
}
