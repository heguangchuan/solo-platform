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
     * SIP 监听 IP；留空表示监听所有网卡
     */
    private String ip;

    /**
     * SIP 公网 IP；NAT/公网部署时必填，用于构造 Via/Contact 头，让下级/设备能正常回包
     * 留空则使用 ip 字段的值
     */
    private String publicIp;

    /**
     * 取构造 Via/Contact 用的 SIP IP：{@link #publicIp} 非空白时返回它，否则回落到本机 IP。
     * <p>注意：Spring Boot 加载 YAML 会把 {@code public-ip:}（空值）拍平成空串 {@code ""} 而非 {@code null}，
     * 所以判空必须看空白，不能 {@code != null}——否则空串会被当成有效公网 IP 写进 Via host，
     * 导致对端按空 host 无法回包（设备查询全部无响应）。</p>
     *
     * @param localIp 本机接入网卡 IP（{@code SipLayer.getLocalIp} 的结果）
     * @return 有效的 SIP IP
     */
    public String resolveSipIp(String localIp) {
        return (publicIp == null || publicIp.isBlank()) ? localIp : publicIp;
    }

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
     * 级联下级侧配置（本平台作为下级向上级平台推送数据时使用）。
     */
    private Cascade cascade = new Cascade();

    /**
     * Catalog 分片累积调优（下级/平台设备的 Catalog 响应处理用）。
     * <p>设备通告的 SumNum 在协议层无"完成"信号，平台只能靠启发式判断何时收尾：
     * 完全无响应（expire）→ 强制收尾，防止 4w+ 大目录慢流时内存堆积。</p>
     */
    private Catalog catalog = new Catalog();

    /**
     * 通道目录同步专用线程池调优（落库任务，IO 密集）。
     * <p>独立于业务线程池：Catalog 同步是 10w+ 通道下的重活，不能与 SIP 消息分发挤同一个池。</p>
     */
    private CatalogSyncPool catalogSyncPool = new CatalogSyncPool();

    /**
     * 移动位置（GPS 轨迹）配置。
     */
    private Position position = new Position();

    /**
     * 级联下级侧配置。
     * <p>本平台作为下级向上级平台推送数据（Catalog 响应、Notify 变更）时的分片/节奏控制。</p>
     */
    @Data
    public static class Cascade {

        /**
         * Catalog 响应分片大小：每包 XML 中 &lt;Item&gt; 数量上限。
         * <p>过大易触发上级 SIP MSG 长度限制或 UDP 分片，过小徒增交互次数。主流开源国标平台默认 3-8，
         * 1/2/4/8 是经过线上踩坑收敛出的「单包安全档位」（ADR-027）。本项目取 3（保守起步）。</p>
         */
        private int catalogPageSize = 3;

        /**
         * Catalog 响应等 ACK 超时（毫秒）。每发一片 MESSAGE，等上级回 200 OK 的最长等待时间；
         * 超时则按 {@link #catalogPageIntervalMs} 兜底节奏发下一片，避免对端死掉时整条链路卡死。
         * <p>5s 粒度受 {@link com.solo.gb28181.task.GbScheduledTasks#sweepSipSubscribe} 限制，
         * 实际最坏情况 ≈ 5s + 3s。3s 适合内网主流平台；公网 / 老国标可上调到 5-8s。</p>
         */
        private long catalogAckTimeoutMs = 3000;

        /**
         * Catalog 响应 fallback 间隔（毫秒）。仅当上一片等 ACK 超时 / 出错时使用——按兜底节奏继续发下一片，
         * 防止对端长时间无响应时本平台 sender 线程被长时间占用。ACK 正常到达时不走这条（ADR-027 改为 ACK 驱动）。
         * <p>UDP 突发易被上级丢包或触发 fair-queuing，200ms 足够宽松且最坏情况（兜底分支）总耗时可控。</p>
         */
        private long catalogPageIntervalMs = 200;

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
     * {@link #expireMs} 用来兜底——完全多少毫秒无任何响应（含空响应）→ 强制收尾，
     * 防止 4w+ 大目录慢流时内存堆积。其余几项是"边收边落"的刷盘策略。普通小目录通常 10s 内收齐，
     * 根本走不到 expireMs 这条路径。</p>
     */
    @Data
    public static class Catalog {

        /**
         * 强制收尾：完全多少毫秒无任何响应 → sweep 路径 partial finalize，释放内存。
         * <p>4w+ 大目录 + 慢设备场景下兜底用，普通 1k 通道规模无需调整。默认 600s（10 分钟）。</p>
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
         * 时间触发 flush 间隔（毫秒）。距上次 flush 超过此间隔且缓冲非空，强制 flush 一次。
         * <p>解决"设备发包慢（每分片 0-1 个 item）+ flushThreshold 较大 → 进度条长期不动"
         * 问题：每 N 毫秒即便缓冲未达阈值也触发 flush，让前端进度能持续滚动。
         * 太小 → 频繁空 flush 浪费 DB；太大 → 进度条更新卡顿。默认 5000。</p>
         */
        private long flushIntervalMs = 5000L;

        /**
         * 单批入库 SQL 的行数上限。MySQL 单条 INSERT 行数受 max_allowed_packet 限制，
         * 500-1000 行是安全区间。flush 缓冲 ≥ 该值时分片入库，每片独立执行 upsert SQL。
         */
        private int batchSize = 500;

        /**
         * 丢包容忍：只有累计收到 ≥ sumNum × 阈值比例时，才删"已不存在的通道"（清空设备目录）；
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

    /**
     * 移动位置（GPS 轨迹）配置。
     * <p>设备可按 5s/次高频上报 MobilePosition，全量长期保留会让 {@code gb_mobile_position}
     * 无限膨胀，因此按保留天数每日清理过期数据。</p>
     */
    @Data
    public static class Position {

        /**
         * 轨迹保留天数，超过的记录每日凌晨清理。设为 0 或负数表示不自动清理。默认 30 天。
         */
        private int retentionDays = 30;
    }
}
