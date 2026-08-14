package com.solo.media.service;

import com.solo.media.entity.MediaServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 流媒体节点负载索引
 *
 * <h3>职责</h3>
 * <p>用 Redis ZSet 记录每台 ZLM 当前承载的活跃流数，作为 {@link MediaServerSelector} 选点
 * 的真实负载依据。ZSet 成员 = {@code mediaServerId}，分数 = 该 ZLM 上活跃流数。</p>
 *
 * <h3>数据流</h3>
 * <ul>
 *     <li>{@link #onNodeOnline} —— 节点首次触达时入索引（{@code addIfAbsent}，避免覆盖已有分数）</li>
 *     <li>{@link #onNodeOffline} —— 节点从索引移除</li>
 *     <li>{@link #onStreamArrival} / {@link #onStreamDeparture} —— 每次国标点播/回放流就绪/断开时分数 ±1</li>
 *     <li>{@link #pickLeastLoaded} —— 选点：升序取 ZSet 首成员 = 当前最闲的节点</li>
 * </ul>
 *
 * <h3>边界</h3>
 * <p>只对国标 rtp 应用（app=rtp + schema=rtsp）的点播/回放流计数，推流（originType=PUSH）的
 * stream 不计入。理由：推流是 ZLM 主动收的，不属于本平台编排的"负载"。</p>
 *
 * <h3>Redis 抖动兜底</h3>
 * <p>{@link #pickLeastLoaded} 返回 null 时（ZSet 为空 / Redis 不可达），调用方
 * {@link MediaServerSelector} 走 status=1 第一台兜底，保证点播接口不因 Redis 故障 500。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaServerLoader {

    /**
     * ZSet key：成员 = mediaServerId，分数 = 活跃流数
     */
    private static final String ONLINE_KEY = "media:server:online";

    private final StringRedisTemplate redis;
    private final MediaServerService mediaServerService;

    /**
     * 节点入索引：首次触达时分数初始化为 0
     *
     * @param serverId ZLM 节点 ID
     */
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
     * 节点出索引：通常在管理面删除节点时调用；ZLM 异常掉线时分数可能暂时残留，靠下次
     * {@code onNodeOnline} 重新入索引时覆盖
     *
     * @param serverId ZLM 节点 ID
     */
    public void onNodeOffline(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        redis.opsForZSet().remove(ONLINE_KEY, serverId);
    }

    /**
     * 流就绪 +1
     */
    public void onStreamArrival(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        Double newScore = redis.opsForZSet().incrementScore(ONLINE_KEY, serverId, 1);
        log.debug("[流媒体] 流就绪 +1 serverId={} score={}", serverId, newScore);
    }

    /**
     * 流断开 -1
     */
    public void onStreamDeparture(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        Double newScore = redis.opsForZSet().incrementScore(ONLINE_KEY, serverId, -1);
        log.debug("[流媒体] 流断开 -1 serverId={} score={}", serverId, newScore);
    }

    /**
     * 选当前最闲的节点
     *
     * <p>ZSet 升序取首位 = 分数最低 = 活跃流数最少。返回 null 表示 ZSet 为空
     * （冷启动 / Redis 抖动 / 节点刚被清空），调用方需自行兜底。</p>
     *
     * @return 最闲节点；null 表示无任何在线节点
     */
    public MediaServer pickLeastLoaded() {
        Set<String> ids = redis.opsForZSet().range(ONLINE_KEY, 0, 0);
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        String serverId = ids.iterator().next();
        return mediaServerService.lambdaQuery()
                .eq(MediaServer::getServerId, serverId)
                .one();
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
}
