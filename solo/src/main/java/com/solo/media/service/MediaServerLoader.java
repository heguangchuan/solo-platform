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

    // ZSet 分数就是活跃流数。
    private static final String ONLINE_KEY = "media:server:online";

    private final StringRedisTemplate redis;

    // 离线节点先给个超大分，选最闲节点时就不会撞上它。
    private static final double OFFLINE_SCORE = 1_000_000_000d;

    public void onNodeOnline(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        redis.opsForZSet().add(ONLINE_KEY, serverId, 0);
        log.info("[流媒体节点] 节点入负载索引 serverId={} score=0", serverId);
    }

    public void onNodeOffline(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        redis.opsForZSet().add(ONLINE_KEY, serverId, OFFLINE_SCORE);
        log.info("[流媒体节点] 节点离线下线，负载分置为占位值 serverId={}", serverId);
    }

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
        log.debug("[流媒体节点] 流就绪 +1 serverId={} score={}", serverId, newScore);
    }

    public void onStreamDeparture(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        Double newScore = redis.opsForZSet().incrementScore(ONLINE_KEY, serverId, -1);
        // 重复 hook 可能把分数减成负数，那反而成了“最闲节点”，拉回 0
        if (newScore != null && newScore < 0) {
            redis.opsForZSet().add(ONLINE_KEY, serverId, 0);
            log.warn("[流媒体节点] 负载分钳位到 0 serverId={} 原值={}", serverId, newScore);
            newScore = 0d;
        }
        log.debug("[流媒体节点] 流断开 -1 serverId={} score={}", serverId, newScore);
    }

    public String pickLeastLoadedServerId() {
        Set<String> ids = redis.opsForZSet().range(ONLINE_KEY, 0, 0);
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.iterator().next();
    }

    public Double getLoad(String serverId) {
        if (serverId == null) {
            return null;
        }
        return redis.opsForZSet().score(ONLINE_KEY, serverId);
    }

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
