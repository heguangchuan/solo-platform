package com.solo.gb28181.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "gb28181")
public class Gb28181Properties {

    // 留空监听所有网卡
    private String ip;

    // NAT/公网部署要填，用来写 Via/Contact；留空就用本机 IP
    private String publicIp;

    // YAML 里 public-ip 留空，读到的是 ""，不是 null。
    // 之前把空串写进 Via，设备回包全丢了，所以这里必须判 isBlank。
    public String resolveSipIp(String localIp) {
        return (publicIp == null || publicIp.isBlank()) ? localIp : publicIp;
    }

    private int port = 5060;

    // 本平台 20 位国标编码
    private String id;

    private String domain;

    // 留空就不验 Digest，只能在隔离测试环境这么玩
    private String password;

    private int registerExpires = 3600;

    // 超过这个倍数的心跳周期还没收到心跳，就算离线
    private int keepaliveTimeoutMultiplier = 3;

    // DeviceInfo 手动探测超时；公网和老设备别调太小
    private long detectTimeoutMs = 10000;

    // true 用收包源地址，NAT 后比较稳；false 才看 Via 的 received/rport
    private boolean useSourceIpAsRemoteAddress = true;

    private Integer corePoolSize;

    private Integer maxPoolSize;

    private Integer queueCapacity;

    private Cascade cascade = new Cascade();

    private Catalog catalog = new Catalog();

    private CatalogSyncPool catalogSyncPool = new CatalogSyncPool();

    private Position position = new Position();

    private Record record = new Record();

    @Data
    public static class Cascade {

        // 单包别贪大，超过 8 有些上级会拒收；线上一般就用 1/2/4/8
        private int catalogPageSize = 3;

        // 每片先等 ACK，超时再按下面的间隔发。公网/老设备可调到 5-8s
        private long catalogAckTimeoutMs = 3000;

        // 只在等 ACK 超时/出错时用，正常收到 ACK 不走这里
        private long catalogPageIntervalMs = 200;

        // ZLM 向上级发流的本地端口段，要避开收流端口并放通防火墙
        private int sendRtpPortStart = 50000;

        private int sendRtpPortEnd = 60000;

        // 回了 200 OK 还等不到 ACK，就释放会话并 BYE 上级
        private long sendAckTimeoutMs = 60_000;
    }

    // GB28181 没有“目录发完了”的信号，只能按 SumNum 和超时猜。
    @Data
    public static class Catalog {

        // 完全没响应这么久就强制收尾，别让大目录一直占内存
        private long expireMs = 600_000L;

        // 攒够就入库，不傻等 SumNum；调大省 SQL，但更占内存
        private int flushThreshold = 1000;

        // 慢设备可能半天凑不够一批，到时间也刷一次，前端进度别一直不动
        private long flushIntervalMs = 5000L;

        // 单条 upsert 别太大，500-1000 行一般比较稳
        private int batchSize = 500;

        // 收到不到这个比例，可能正在丢包，先别删旧通道；默认允许缺 5%
        private double completenessThreshold = 0.95;
    }

    // 目录落库单独一个池，别跟 SIP 消息抢线程。
    // 池满后由调用线程执行，顺便压住目录写入速度。
    @Data
    public static class CatalogSyncPool {

        private int corePoolSize = 20;

        private int maxPoolSize = 50;

        private int queueCapacity = 5000;
    }

    @Data
    public static class Position {

        // 0 或负数表示不自动清理
        private int retentionDays = 30;
    }

    @Data
    public static class Record {

        // 计划里配了保留天数就听计划的；计划为 0 才用这个全局值，0 或负数不自动清理
        private int retentionDays = 30;

        // 源 URL 是 ZLM 本机地址，重启或切片清理后基本拉不回来了，重试别没完了
        private int maxRetry = 3;

        // UPLOADING 卡这么久就重新领，单位分钟
        private int uploadingStuckMinutes = 30;
    }
}
