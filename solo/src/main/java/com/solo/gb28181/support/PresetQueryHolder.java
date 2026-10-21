package com.solo.gb28181.support;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;


@Slf4j
public class PresetQueryHolder {

    /** SN → 通道id */
    private static final ConcurrentHashMap<Integer, Long> MAP = new ConcurrentHashMap<>();

    /** 超时时长（毫秒）：30 秒还没响应就丢弃，避免内存泄漏 */
    private static final long TTL_MS = 30_000L;

    /** SN → 注册时间戳（用于过期清理） */
    private static final ConcurrentHashMap<Integer, Long> TIME_MAP = new ConcurrentHashMap<>();

    private PresetQueryHolder() {}

    /**
     * 注册一次预置位查询
     *
     * @param sn          查询 SN
     * @param channelDbId 通道数据库主键
     */
    public static void register(int sn, Long channelDbId) {
        MAP.put(sn, channelDbId);
        TIME_MAP.put(sn, System.currentTimeMillis());
    }

    /**
     * 消费一次（取出并移除）
     *
     * @param sn 查询 SN
     * @return 通道数据库主键；不存在或已过期返回 null
     */
    public static Long consume(int sn) {
        Long channelDbId = MAP.remove(sn);
        Long ts = TIME_MAP.remove(sn);
        if (channelDbId != null && ts != null) {
            if (System.currentTimeMillis() - ts > TTL_MS) {
                log.warn("[预置位查询] SN 已过期，丢弃 sn={}", sn);
                return null;
            }
        }
        return channelDbId;
    }
}
