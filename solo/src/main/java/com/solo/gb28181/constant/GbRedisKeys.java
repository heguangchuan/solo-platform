package com.solo.gb28181.constant;

/**
 * Redis Key 常量。
 *
 * 命名规则：gb28181:<模块>:<子分类>:<具体key>
 * 统一前缀方便排查和清理。
 */
public interface GbRedisKeys {

    // ===== 设备在线状态 =====

    // 设备在线 key 前缀，值就是 "1"，靠 TTL 维持
    String DEVICE_ONLINE_PREFIX = "gb28181:device:online:";

    /**
     * 设备档案缓存前缀，存整个 GbDevice 对象，TTL 10 分钟。
     *
     * 为啥搞这个：SIP 入口（MESSAGE 处理之类的）每条消息都要查设备信息，
     * catalog 同步的时候一个设备能发几万条 MESSAGE，每条都查 DB 扛不住。
     * 首条 miss 查库放缓存，后面的都走缓存。
     *
     * 主动失效时机：设备上下线、手动更新、删除、catalog 同步完回写。
     * TTL 只是兜底。
     */
    String DEVICE_PROFILE_PREFIX = "gb28181:device:profile:";

    /**
     * 接入禁用名单，Redis Set，存被禁用的 deviceId。
     *
     * 背景：SIP 入口每条消息都要判断是否被拉黑，catalog 同步的时候消息量太大，
     * 每条查 gb_platform 表不行。整表加载成 Set 放 Redis，TTL 5 分钟，
     * 增删改的时候主动删掉让它重建。
     *
     * 为啥用 Set 不用逐个 key：黑名单本来就没几条（几条到几十条），
     * 一个 Set 存完，SISMEMBER O(1)，比一堆 key 省内存也好管理。
     */
    String DEVICE_BLOCKED_SET = "gb28181:device:blocked";

    // ===== 点播/回放会话 =====

    // 会话前缀
    String INVITE_PREFIX = "gb28181:invite:";

    // 会话主体，key = s:{streamId}
    String INVITE_SESSION = INVITE_PREFIX + "s:";

    // Call-ID 索引：c:{callId} → streamId
    String INVITE_BY_CALL = INVITE_PREFIX + "c:";

    // SSRC 索引：y:{ssrc} → streamId
    String INVITE_BY_SSRC = INVITE_PREFIX + "y:";

    // 设备通道索引：d:{deviceId}:{channelId} → Set<streamId>
    // 一个通道可能同时有主流和子码流两路
    String INVITE_BY_DEVICE_CHANNEL = INVITE_PREFIX + "d:";

    // 所有活跃会话的全集 Set
    String INVITE_ALL_ACTIVE = INVITE_PREFIX + "all";

    // ===== 级联推流会话（作为下级被上级点播，sendRtp 往上推）=====

    String CASCADE_SEND_PREFIX = "gb28181:cascade:send:";

    // 会话主体：s:{sendStreamId}
    String CASCADE_SEND_SESSION = CASCADE_SEND_PREFIX + "s:";

    // 上级 INVITE 的 Call-ID 索引：c:{callId} → sendStreamId
    String CASCADE_SEND_BY_CALL = CASCADE_SEND_PREFIX + "c:";

    // 源流索引：st:{srcStream} → Set<sendStreamId>，一路源流可能被多个上级点播
    String CASCADE_SEND_BY_SRC_STREAM = CASCADE_SEND_PREFIX + "st:";

    // 所有活跃推流会话全集
    String CASCADE_SEND_ALL_ACTIVE = CASCADE_SEND_PREFIX + "all";

    // 本地发送端口分配的自增计数器
    String CASCADE_SEND_PORT_SEQ = CASCADE_SEND_PREFIX + "portseq";

    // ===== 语音广播会话（平台喊话设备，浏览器麦克风 → ZLM → startSendRtp 推设备）=====

    String BROADCAST_PREFIX = "gb28181:broadcast:";

    // 会话主体：s:{broadcastStreamId}
    String BROADCAST_SESSION = BROADCAST_PREFIX + "s:";

    // 设备反向 INVITE 的 Call-ID 索引：c:{callId} → broadcastStreamId
    String BROADCAST_BY_CALL = BROADCAST_PREFIX + "c:";

    // 源流索引：st:{srcStream} → Set<broadcastStreamId>
    String BROADCAST_BY_SRC_STREAM = BROADCAST_PREFIX + "st:";

    // 所有活跃广播会话全集
    String BROADCAST_ALL_ACTIVE = BROADCAST_PREFIX + "all";

    // 本地发送端口分配的自增计数器（复用级联端口段配置，计数器独立）
    String BROADCAST_PORT_SEQ = BROADCAST_PREFIX + "portseq";
}
