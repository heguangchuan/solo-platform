package com.solo.media.task;

import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.service.StreamProxyService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * <h3>为什么需要</h3>
 * <p>ZLM 对 {@code addStreamProxy} 创建的流不像 push 那样有 {@code on_stream_changed} 兜底，
 * 源流挂了 / ZLM 重启 / 网络分区都不会主动通知。{@code stream_proxy.status=1} 是
 * {@code addStreamProxy} 调用的结果快照，不反映 ZLM 实时状态。
 * 本任务每 30s 拉一次 ZLM 的 {@code getMediaList} 跟 DB 对账，把"库说在跑但 ZLM 已经没了"的
 * 行标回 status=0 + lastError。</p>
 *
 * <h3>对账语义</h3>
 * <ul>
 *   <li>DB 标 status=1 + ZLM 有这条流 → 保持</li>
 *   <li>DB 标 status=1 + ZLM 没有 → status=0 + lastError="源流已不可达"</li>
 *   <li>DB 标 status=0 → 跳过（停用的不主动重启，等用户点"启用"）</li>
 * </ul>
 *
 * <h3>触发点</h3>
 * <ul>
 *   <li>定时：{@code MediaScheduledTasks.reconcileStreamProxies} 每 30s 调一次</li>
 *   <li>响应：{@code ZlmHookService.onServerStarted} 节点刚启动时立即对账</li>
 * </ul>
 *
 * <h3>多 solo 节点并发</h3>
 * <p>沿用 {@link MediaServerHealthDetector} 的 Redis SETNX 锁模式：抢到锁的本轮执行，其他跳过。
 * 对账是幂等的（status 写覆盖），重复执行不会有副作用。</p>
 *
 * <h3>ZLM 不可达时的处理</h3>
 * <p>如果某台 ZLM 离线，{@link com.solo.media.client.ZlmRestClient#getMediaList} 内部
 * 抓异常返空列表。本任务会把"这台 ZLM 上所有 status=1 的代理"全标成 status=0 —
 * 这是合理语义（"我们看不到它，所以按不可用处理"），代价是 ZLM 短暂抖动会误标，
 * 下轮对账或用户点"启用"即可恢复。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamProxyReconciler {

    /**
     * 对账 leader 选举锁：仅一个 solo 实例本轮持有，持锁期间独占对账权
     */
    private static final String LOCK_KEY = "media:proxy:reconcile:lock";
    /**
     * 锁 TTL 略小于对账周期，让下一轮 tick 能重新选主
     */
    private static final Duration LOCK_TTL = Duration.ofSeconds(25);

    private final StringRedisTemplate redis;
    private final GbMediaServerMapper mediaServerMapper;
    private final StreamProxyService streamProxyService;

    /**
     * 当前 solo 实例标识：启动时生成，仅用于日志可追溯
     */
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    @PostConstruct
    void logInstanceId() {
        log.info("[拉流代理对账] 实例标识已生成 instanceId={}", instanceId);
    }

    public void reconcile() {
        if (!tryAcquireLock()) {
            log.debug("[拉流代理对账] 抢锁失败，本轮跳过 instanceId={}", instanceId);
            return;
        }
        try {
            List<MediaServer> servers = mediaServerMapper.selectList(null);
            log.info("[拉流代理对账] 巡检开始 instanceId={} 节点数={}", instanceId, servers.size());
            int totalCleaned = 0;
            for (MediaServer s : servers) {
                try {
                    streamProxyService.reconcileWithZlm(s.getServerId());
                } catch (Exception e) {
                    // 单台对账失败不影响其他台
                    log.warn("[拉流代理对账] 单节点失败 serverId={} err={}",
                            s.getServerId(), e.getMessage());
                }
            }
            log.info("[拉流代理对账] 巡检完成 instanceId={}", instanceId);
        } finally {
            releaseLock();
        }
    }

    /**
     * 单节点对账：由 {@code ZlmHookService.onServerStarted} 触发
     */
    public void reconcileServer(String mediaServerId) {
        if (mediaServerId == null) return;
        try {
            streamProxyService.reconcileWithZlm(mediaServerId);
        } catch (Exception e) {
            log.warn("[拉流代理对账] 响应式触发失败 serverId={} err={}",
                    mediaServerId, e.getMessage());
        }
    }

    private boolean tryAcquireLock() {
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(LOCK_KEY, instanceId, LOCK_TTL);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            // Redis 异常时降级放行，不阻塞对账
            return true;
        }
    }

    private void releaseLock() {
        try {
            redis.delete(LOCK_KEY);
        } catch (Exception e) {
            log.debug("[拉流代理对账] 释放锁失败（不影响）err={}", e.getMessage());
        }
    }
}
