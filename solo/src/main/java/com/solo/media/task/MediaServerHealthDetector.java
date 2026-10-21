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

/**
 * <h3>职责</h3>
 * <p>每 10s 遍历所有 {@code media_server} 登记行，对每个 ZLM 调 {@code /index/api/version}
 * 探活，根据结果回写 MySQL {@code status / hook_alive_time} 与 Redis 负载索引：</p>
 * <ul>
 *   <li>成功 → status=1 + hookAliveTime=now + {@link MediaServerLoader#onNodeOnline}</li>
 *   <li>失败 → status=0 + {@link MediaServerLoader#onNodeOffline}</li>
 * </ul>
 * <p>补齐原先 push-only 模型的缺口：ZLM 进程死掉 / 机器断电 / 网络隔离时，
 * 没人调 hook，{@code status} 会一直停在 1——本任务把这一档填上。</p>
 *
 * <h3>多 solo 节点部署的并发</h3>
 * <p>每个 solo 实例都跑这个探测任务，会造成 N 倍探测流量 + 状态写竞争。
 * 用 Redis SETNX 锁做简易 leader election：抢到锁的实例本轮执行探测，其他实例跳过。
 * 锁 TTL（8s）< 探测周期（10s），下一轮重新抢主，躲过单实例故障。leader 写完后立即释放锁，
 * 让其他实例下一轮有机会轮替，避免永久粘在同一实例上。</p>
 *
 * <h3>探测超时</h3>
 * <p>{@link ZlmRestClient#ping} 内部 5s 连接 + 10s 请求超时，对单台死 ZLM 探测要等满 5s。
 * 多个节点串行探测最坏 = 节点数 × 5s，远大于锁 TTL。这里用
 * {@link CompletableFuture#orTimeout} 并行探测 + 单路 3s 兜底超时，
 * 把总时长压在 3s 以内，保证锁内可完成。</p>
 *
 * <h3>Redis 抖动兜底</h3>
 * <p>抢锁 / 释放锁遇异常一律降级放行（不抛、不阻塞探测），多实例并发时会出现
 * 重复探测，重复写 status 是幂等的，最多多打几个 ping，可接受。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaServerHealthDetector {

    /**
     * 探测 leader 选举锁：仅一个 solo 实例本轮持有，持锁期间独占探测权
     */
    private static final String LOCK_KEY = "media:health:lock";
    /**
     * 锁 TTL 略小于探测周期，让下一轮 tick 能重新选主
     */
    private static final Duration LOCK_TTL = Duration.ofSeconds(8);
    /**
     * 单路探测兜底超时：覆盖 ZlmRestClient 的 5s 连接超时，3s 失败即视为离线
     */
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);

    private final StringRedisTemplate redis;
    private final GbMediaServerMapper mediaServerMapper;
    private final ZlmRestClient zlmRestClient;
    private final MediaServerLoader loadIndex;

    /**
     * 当前 solo 实例标识：启动时生成，仅用于日志可追溯，Redis 锁的 value 字段
     * 当前不强制校验持有者（释放锁时不 Lua check-and-del），所以这里只是个友好标记
     */
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

            // 并行探测：单路 PROBE_TIMEOUT 兜底，总时长 ≈ 单路超时
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
            // 单节点写失败不影响其他节点：DB 异常 / Redis 异常都吃掉，记日志
            log.warn("[流媒体健康探测] 写状态失败 serverId={} err={}",
                    server.getServerId(), e.getMessage());
        }
        return alive;
    }

    /**
     * 抢探测 leader 锁。Redis 故障时降级放行（不阻塞探测），
     * 多实例并发时会出现重复探测，但 status 写是幂等的。
     */
    private boolean tryAcquireLock() {
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(LOCK_KEY, instanceId, LOCK_TTL);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("[流媒体健康探测] 抢锁异常，降级放行 err={}", e.getMessage());
            return true;
        }
    }

    /**
     * 释放探测 leader 锁。释放失败不影响业务——锁靠 TTL 兜底过期，
     * 下一轮 tick 重新选主即可。
     */
    private void releaseLock() {
        try {
            redis.delete(LOCK_KEY);
        } catch (Exception e) {
            log.debug("[流媒体健康探测] 释放锁失败（不影响）err={}", e.getMessage());
        }
    }
}
