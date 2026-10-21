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

    public void onNodeOnline(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        Boolean isNew = redis.opsForZSet().addIfAbsent(ONLINE_KEY, serverId, 0);
        if (Boolean.TRUE.equals(isNew)) {
            log.info("[流媒体] 节点入负载索引 serverId={} score=0", serverId);
        }
    }

    /**
     * 通常在管理面删除节点时调用；ZLM 异常掉线时分数可能暂时残留，靠下次
     * {@code onNodeOnline} 重新入索引时覆盖
     */
    public void onNodeOffline(String serverId) {
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
