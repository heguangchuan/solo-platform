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
}
