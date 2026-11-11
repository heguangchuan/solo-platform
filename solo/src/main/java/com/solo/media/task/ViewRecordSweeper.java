package com.solo.media.task;

import com.solo.media.properties.MediaProperties;
import com.solo.media.service.GbViewRecordService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 关掉僵在"观看中"的记录。正常情况 on_flow_report 会闭合，
 * 节点重启 / 网络分区把 hook 全丢了时，只能靠这个兜底。
 * 多实例用 Redis SETNX 选主，同一轮只有一个实例跑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewRecordSweeper {

    private static final String LOCK_KEY = "media:view-record:sweep:lock";
    /** TTL 略小于 10min 调度周期，实例挂了锁也能自动过期，下轮重新选主 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(540);

    private final StringRedisTemplate redis;
    private final GbViewRecordService viewRecordService;
    private final MediaProperties mediaProperties;

    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    @PostConstruct
    void logInstanceId() {
        log.info("[观看记录清扫] 实例标识 instanceId={}", instanceId);
    }

    public void sweep() {
        if (!tryAcquireLock()) {
            log.debug("[观看记录清扫] 抢锁失败，本轮跳过 instanceId={}", instanceId);
            return;
        }
        try {
            int staleMinutes = mediaProperties.getViewRecord().getSweepStaleMinutes();
            int closed = viewRecordService.sweepOpenRecords(staleMinutes);
            if (closed > 0) {
                log.info("[观看记录清扫] 本轮兜底关闭 {} 条 instanceId={}", closed, instanceId);
            }
        } finally {
            releaseLock();
        }
    }

    private boolean tryAcquireLock() {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(LOCK_KEY, instanceId, LOCK_TTL));
        } catch (Exception e) {
            // Redis 挂了降级放行：只处理 OPEN 行，重复跑最多多一次覆盖更新，不会产生重复数据
            log.warn("[观看记录清扫] 抢锁异常，降级直接执行 err={}", e.getMessage());
            return true;
        }
    }

    private void releaseLock() {
        try {
            redis.delete(LOCK_KEY);
        } catch (Exception e) {
            log.debug("[观看记录清扫] 释放锁失败（不影响）err={}", e.getMessage());
        }
    }
}
