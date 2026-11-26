package com.solo.media.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaScheduledTasks {

    private final MediaServerHealthDetector healthDetector;
    private final StreamProxyReconciler proxyReconciler;
    private final ViewRecordSweeper viewRecordSweeper;

    @Scheduled(initialDelay = 5_000, fixedDelay = 10_000)
    public void probeMediaServers() {
        try {
            healthDetector.probe();
        } catch (Exception e) {
            log.warn("[流媒体定时] 健康探测异常 {}", e.getMessage(), e);
        }
    }

    @Scheduled(initialDelay = 15_000, fixedDelay = 30_000)
    public void reconcileStreamProxies() {
        try {
            proxyReconciler.reconcile();
        } catch (Exception e) {
            log.warn("[流媒体定时] 拉流代理对账异常 {}", e.getMessage(), e);
        }
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 10 * 60_000)
    public void sweepViewRecords() {
        try {
            viewRecordSweeper.sweep();
        } catch (Exception e) {
            log.warn("[流媒体定时] 观看记录清扫异常 {}", e.getMessage(), e);
        }
    }
}
