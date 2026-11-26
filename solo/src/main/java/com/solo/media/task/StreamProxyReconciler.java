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

// addStreamProxy 的流没有 on_stream_changed 兜底，DB 里的 status=1 只是上次调用结果，得定时跟 ZLM 对。
// 停用的代理不自动拉起。
// ZLM 连不上时 getMediaList 会返空，这轮会把该节点代理全标离线；真误标了就手动启用。
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamProxyReconciler {

    private static final String LOCK_KEY = "media:proxy:reconcile:lock";
    private static final Duration LOCK_TTL = Duration.ofSeconds(25);

    private final StringRedisTemplate redis;
    private final GbMediaServerMapper mediaServerMapper;
    private final StreamProxyService streamProxyService;

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
                    log.warn("[拉流代理对账] 单节点失败 serverId={} err={}",
                            s.getServerId(), e.getMessage());
                }
            }
            log.info("[拉流代理对账] 巡检完成 instanceId={}", instanceId);
        } finally {
            releaseLock();
        }
    }

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
            // Redis 挂了顶多重复对账，别因此不跑
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
