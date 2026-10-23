package com.solo.media.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;


@Slf4j
@Component
@RequiredArgsConstructor
public class MediaServerLoader {

    /**
     * ZSet key：成员 = mediaServerId，分数 = 活跃流数
     */
    private static final String ONLINE_KEY = "media:server:online";

    private final StringRedisTemplate redis;

    /**
     * 离线节点在 ZSet 中的占位分：足够大，保证排在所有在线节点后面，
     * 不会被 {@link #pickLeastLoadedServerId()} 选中。
     */
    private static final double OFFLINE_SCORE = 1_000_000_000d;

    public void onNodeOnline(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        // 节点上线时重置为 0：
        // - 首次上线：新成员，score=0
        // - 离线后恢复：从 OFFLINE_SCORE 重置为 0，等待流事件重新累计
        // - 一直在线但分数漂移：修正回 0（热修复，后续流事件会补正）
        redis.opsForZSet().add(ONLINE_KEY, serverId, 0);
        log.info("[流媒体] 节点入负载索引 serverId={} score=0", serverId);
    }

    /**
     * 节点离线时将分数设为极大值（不 remove），避免被负载均衡选中；
     * 同时防止迟到的流离开事件（ZINCRBY）在成员不存在时从 0 减成负数，导致"负分优先"的异常选点。
     * 管理面删除节点时再调用 {@link #removeNode(String)} 彻底移除。
     */
    public void onNodeOffline(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        redis.opsForZSet().add(ONLINE_KEY, serverId, OFFLINE_SCORE);
        log.info("[流媒体] 节点离线下线，负载分置为占位值 serverId={}", serverId);
    }

    /**
     * 管理面删除节点时调用，彻底从 ZSet 中移除。
     */
    public void removeNode(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        redis.opsForZSet().remove(ONLINE_KEY, serverId);
    }

    public void onStreamArrival(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        Double newScore = redis.opsForZSet().incrementScore(ONLINE_KEY, serverId, 1);
        log.debug("[流媒体] 流就绪 +1 serverId={} score={}", serverId, newScore);
    }

    public void onStreamDeparture(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        Double newScore = redis.opsForZSet().incrementScore(ONLINE_KEY, serverId, -1);
        // 下限保护：流事件时序异常（如节点已离线但仍收到流离开、重复 hook 等）可能导致负数，
        // 负数会让负载均衡误判为"最闲节点"，这里强制钳位到 0。
        if (newScore != null && newScore < 0) {
            redis.opsForZSet().add(ONLINE_KEY, serverId, 0);
            log.warn("[流媒体] 负载分钳位到 0 serverId={} 原值={}", serverId, newScore);
            newScore = 0d;
        }
        log.debug("[流媒体] 流断开 -1 serverId={} score={}", serverId, newScore);
    }

    /**
     * <p>ZSet 升序取首位 = 分数最低 = 活跃流数最少。本方法<strong>只返回 {@code serverId} 字符串</strong>，
     * 拿到完整实体（IP / 端口 / secret 等）由调用方去 MySQL 查——避免 Loader 反向依赖 Service。</p>
     *
     * @return 最闲节点的 serverId；null 表示 ZSet 为空（冷启动 / Redis 抖动 / 节点刚被清空），
     *         调用方需自行兜底
     */
    public String pickLeastLoadedServerId() {
        Set<String> ids = redis.opsForZSet().range(ONLINE_KEY, 0, 0);
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.iterator().next();
    }

    /**
     * 仅查询负载分（不触发选点），供监控 / 调试用
     *
     * @return null 表示该节点不在索引中
     */
    public Double getLoad(String serverId) {
        if (serverId == null) {
            return null;
        }
        return redis.opsForZSet().score(ONLINE_KEY, serverId);
    }

    /**
     * 全网 ZLM 活跃流总数 = 所有 ZSet 成员的 score 之和。
     *
     * <p>供仪表盘 / 监控面板一站式读"当前全网承载多少路流"——
     * 避免调用方自己遍历 ZSet 累加（同样的 SQL 已经在 {@code onStreamArrival / onStreamDeparture}
     * 里同步维护过）。Redis 不可达或索引空时返回 0，不抛异常。</p>
     */
    public long getTotalLoad() {
        try {
            Set<String> ids = redis.opsForZSet().range(ONLINE_KEY, 0, -1);
            if (ids == null || ids.isEmpty()) {
                return 0L;
            }
            long total = 0;
            for (String id : ids) {
                Double score = redis.opsForZSet().score(ONLINE_KEY, id);
                if (score != null) {
                    total += score.longValue();
                }
            }
            return total;
        } catch (Exception e) {
            // Redis 异常时返回 0，不阻塞仪表盘渲染
            return 0L;
        }
    }
}
