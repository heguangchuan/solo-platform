package com.solo.gb28181.constant;

/**
 * GB28181 Redis Key 常量定义
 * <p>
 * 命名规范：统一前缀 {@code gb28181:} + 模块分类 + 冒号分隔，对齐 wvp-pro 最佳实践
 * 格式：{@code gb28181:<category>:<sub>:<key>}
 * </p>
 */
public interface GbRedisKeys {

    // ========== 设备在线状态 ==========

    /** 设备在线 Key 前缀：gb28181:device:online:{deviceId} */
    String DEVICE_ONLINE_PREFIX = "gb28181:device:online:";

    // ========== 点播/回放会话 ==========

    /** 邀请会话前缀 */
    String INVITE_PREFIX = "gb28181:invite:";

    /** 会话主体：s:{streamId} */
    String INVITE_SESSION = INVITE_PREFIX + "s:";

    /** Call-ID 索引：c:{callId} */
    String INVITE_BY_CALL = INVITE_PREFIX + "c:";

    /** SSRC 索引：y:{ssrc} */
    String INVITE_BY_SSRC = INVITE_PREFIX + "y:";

    /** 设备通道索引：d:{deviceId}:{channelId} */
    String INVITE_BY_DEVICE_CHANNEL = INVITE_PREFIX + "d:";

    /** 所有活跃会话全集 Set */
    String INVITE_ALL_ACTIVE = INVITE_PREFIX + "all";

    // ========== SSRC 池 ==========

    /** SSRC 已占用 Set 前缀：gb28181:ssrc:used:{mediaServerId} */
    String SSRC_USED_PREFIX = "gb28181:ssrc:used:";

    // ========== 目录同步 ==========

    /** 目录同步临时存储：gb28181:catalog:sync:{deviceId}:{sn} */
    String CATALOG_SYNC_PREFIX = "gb28181:catalog:sync:";

    // ========== 订阅 ==========

    /** 订阅过期 Key 前缀：gb28181:subscribe:expire:{type}:{id} */
    String SUBSCRIBE_EXPIRE_PREFIX = "gb28181:subscribe:expire:";

    // ========== 移动位置 ==========

    /** 最新移动位置队列：gb28181:mobile:position:{deviceId} */
    String MOBILE_POSITION_QUEUE_PREFIX = "gb28181:mobile:position:";

    // ========== 录像查询 ==========

    /** 录像查询结果临时存储：gb28181:record:result:{channelId}:{sn} */
    String RECORD_RESULT_PREFIX = "gb28181:record:result:";

    // ========== 媒体服务器 ==========

    /** 在线媒体服务器 Set */
    String MEDIA_ONLINE_SERVERS = "gb28181:media:online-servers";

    /** 媒体服务器信息：gb28181:media:server:{serverId} */
    String MEDIA_SERVER_INFO_PREFIX = "gb28181:media:server:";

    // ========== CSeq 序列号 ==========

    /** CSeq 全局递增：gb28181:cseq:counter */
    String CSEQ_COUNTER = "gb28181:cseq:counter";

    // ========== Redis Pub/Sub 事件广播 ==========

    /** 流变化通知频道：wvp-msg-stream-change */
    String EVENT_STREAM_CHANGE = "gb28181:event:stream-change";

    /** 设备状态变化通知频道 */
    String EVENT_DEVICE_STATUS = "gb28181:event:device-status";

    /** 报警通知频道 */
    String EVENT_ALARM = "gb28181:event:alarm";
}
