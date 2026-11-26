package com.solo.media.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.solo.media.entity.MediaServer;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.service.MediaServerLoader;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

// ZLM 死机时不会发 hook，只能靠这里把 status 打回 0。
// 多实例用 Redis 锁轮流探，锁比 10s 周期短，实例挂了下一轮还能接上。
// 节点并行探测，免得一台死机把整轮拖到超过锁时间；Redis 抖动时重复探几次也没关系。
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaServerHealthDetector {

    private static final String LOCK_KEY = "media:health:lock";
    private static final Duration LOCK_TTL = Duration.ofSeconds(8);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);

    private final StringRedisTemplate redis;
    private final GbMediaServerMapper mediaServerMapper;
    private final ZlmRestClient zlmRestClient;
    private final MediaServerLoader loadIndex;

    // 只用来打日志；释放锁没校验 value，别把这个值当成持锁凭证
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    @PostConstruct
    void logInstanceId() {
        log.info("[流媒体健康探测] 实例标识已生成 instanceId={}", instanceId);
    }

    public void probe() {
        if (!tryAcquireLock()) {
            log.debug("[流媒体健康探测] 抢锁失败，本轮跳过 instanceId={}", instanceId);
            return;
        }
        try {
            List<MediaServer> servers = mediaServerMapper.selectList(null);
            if (servers.isEmpty()) {
                return;
            }

            CompletableFuture<Boolean>[] futures = servers.stream()
                    .map(s -> CompletableFuture.supplyAsync(() -> doProbe(s))
                            .orTimeout(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                            .exceptionally(ex -> false))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).join();

            int online = 0, offline = 0;
            for (CompletableFuture<Boolean> f : futures) {
                if (Boolean.TRUE.equals(f.join())) {
                    online++;
                } else {
                    offline++;
                }
            }
            log.info("[流媒体健康探测] 本轮完成 在线:{} 离线:{} 实例:{}",
                    online, offline, instanceId);
        } finally {
            releaseLock();
        }
    }

    private boolean doProbe(MediaServer server) {
        boolean alive = zlmRestClient.ping(server);
        try {
            if (alive) {
                mediaServerMapper.update(null, new LambdaUpdateWrapper<MediaServer>()
                        .eq(MediaServer::getServerId, server.getServerId())
                        .set(MediaServer::getStatus, 1)
                        .set(MediaServer::getHookAliveTime, LocalDateTime.now()));
                loadIndex.onNodeOnline(server.getServerId());
            } else {
                if (server.getStatus() != null && server.getStatus() == 1) {
                    log.warn("[流媒体健康探测] 节点掉线 serverId={} {}:{}",
                            server.getServerId(), server.getHostIp(), server.getHttpPort());
                }
                mediaServerMapper.update(null, new LambdaUpdateWrapper<MediaServer>()
                        .eq(MediaServer::getServerId, server.getServerId())
                        .set(MediaServer::getStatus, 0));
                loadIndex.onNodeOffline(server.getServerId());
            }
        } catch (Exception e) {
            // 一个节点写状态失败，别影响其他节点
            log.warn("[流媒体健康探测] 写状态失败 serverId={} err={}",
                    server.getServerId(), e.getMessage());
        }
        return alive;
    }

    private boolean tryAcquireLock() {
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(LOCK_KEY, instanceId, LOCK_TTL);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("[流媒体健康探测] 抢锁异常，降级放行 err={}", e.getMessage());
            return true;
        }
    }

    private void releaseLock() {
        try {
            redis.delete(LOCK_KEY);
        } catch (Exception e) {
            log.debug("[流媒体健康探测] 释放锁失败（不影响）err={}", e.getMessage());
        }
    }
}
