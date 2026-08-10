package com.solo.gb28181.constant;

/**
 * GB28181 Redis Key 常量定义
 * <p>
 * 命名规范：统一前缀 {@code gb28181:} + 模块分类 + 冒号分隔
 * 格式：{@code gb28181:<category>:<sub>:<key>}
 * </p>
 */
public interface GbRedisKeys {

    // ========== 设备在线状态 ==========

    /** 设备在线 Key 前缀：gb28181:device:online:{deviceId} */
    String DEVICE_ONLINE_PREFIX = "gb28181:device:online:";

    /**
     * 设备档案缓存前缀：gb28181:device:profile:{deviceId}
     * <p>缓存整个 {@code GbDevice} POJO，TTL 10 分钟。用于 {@code MessageRequestProcessor} 等
     * SIP 入口按 {@code deviceId} 查设备时减少 SQL：catalog 同步期间 4w+ 条 MESSAGE 只在首条 miss。</p>
     * <p>写入路径在 {@code GbDeviceServiceImpl}（online / offline / updateByManual / deleteBatch）
     * 和 {@code CatalogSyncSupport}（final flush 回写 channelCount / lastCatalogSyncTime）触发
     * {@code delete} 主动失效，TTL 是兜底。</p>
     */
    String DEVICE_PROFILE_PREFIX = "gb28181:device:profile:";

    /**
     * 接入禁用名单缓存：gb28181:device:blocked（Redis Set，成员是被禁用的 deviceId）
     * <p>SIP 入口（REGISTER / MESSAGE）每条报文都要判"是否被拉黑"，catalog 同步期间
     * 单设备 4w+ 条 MESSAGE，逐条查 {@code gb_platform} 不可接受。整表加载成一个 Set，
     * TTL 5 分钟；{@code GbPlatformServiceImpl} 的增删改主动 delete 失效。</p>
     * <p>用 Set 而非逐 key：黑名单预期条目很少（几条到几十条），一次 SMEMBERS 加载后
     * SISMEMBER 判定是 O(1)，比 N 个 key 更省内存也更好整体失效。</p>
     */
    String DEVICE_BLOCKED_SET = "gb28181:device:blocked";

    // ========== 点播/回放会话 ==========

    /** 邀请会话前缀 */
    String INVITE_PREFIX = "gb28181:invite:";

    /** 会话主体：s:{streamId} */
    String INVITE_SESSION = INVITE_PREFIX + "s:";

    /** Call-ID 索引：c:{callId} */
    String INVITE_BY_CALL = INVITE_PREFIX + "c:";

    /** SSRC 索引：y:{ssrc} */
    String INVITE_BY_SSRC = INVITE_PREFIX + "y:";

    /** 设备通道索引：d:{deviceId}:{channelId} = Set&lt;streamId&gt;（同通道可并存多路码流） */
    String INVITE_BY_DEVICE_CHANNEL = INVITE_PREFIX + "d:";

    /** 所有活跃会话全集 Set */
    String INVITE_ALL_ACTIVE = INVITE_PREFIX + "all";

    // ========== 级联推流会话（本平台作为下级被上级点播，向上级 sendRtp）==========

    /** 级联推流会话前缀 */
    String CASCADE_SEND_PREFIX = "gb28181:cascade:send:";

    /** 会话主体：s:{sendStreamId} */
    String CASCADE_SEND_SESSION = CASCADE_SEND_PREFIX + "s:";

    /** 上级 INVITE Call-ID 索引：c:{callId} = sendStreamId */
    String CASCADE_SEND_BY_CALL = CASCADE_SEND_PREFIX + "c:";

    /** 源流（ZLM app/stream 里的 stream）索引：st:{srcStream} = Set&lt;sendStreamId&gt;（一路源流可被多个上级点播） */
    String CASCADE_SEND_BY_SRC_STREAM = CASCADE_SEND_PREFIX + "st:";

    /** 所有活跃推流会话全集 Set */
    String CASCADE_SEND_ALL_ACTIVE = CASCADE_SEND_PREFIX + "all";

    /** 本地发送端口分配自增计数器 */
    String CASCADE_SEND_PORT_SEQ = CASCADE_SEND_PREFIX + "portseq";
}
