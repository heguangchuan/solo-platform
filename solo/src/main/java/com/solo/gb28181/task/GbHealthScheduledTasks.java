package com.solo.gb28181.task;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.processor.request.SubscribeHolder;
import com.solo.gb28181.processor.request.message.notify.KeepaliveNotifyMessageHandler;
import com.solo.gb28181.service.GbMobilePositionService;
import com.solo.gb28181.service.impl.CascadeSendService;
import com.solo.gb28181.service.impl.GbBroadcastService;
import com.solo.gb28181.sip.SipSubscribe;
import com.solo.gb28181.support.CatalogSyncStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GbHealthScheduledTasks {

    private final DeviceOfflineDetector deviceOfflineDetector;
    private final KeepaliveNotifyMessageHandler keepaliveNotifyMessageHandler;
    private final SipSubscribe sipSubscribe;
    private final SubscribeHolder subscribeHolder;
    private final CascadeSendService cascadeSendService;
    private final GbBroadcastService gbBroadcastService;
    private final GbMobilePositionService mobilePositionService;
    private final CatalogSyncStatusService catalogSyncStatusService;
    private final Gb28181Properties gb28181Properties;

    @Scheduled(fixedDelay = 30_000)
    public void detectDeviceOffline() {
        deviceOfflineDetector.detect();
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
    public void sweepCatalogSyncTimeouts() {
        int timedOut = catalogSyncStatusService.sweepRunningTimeouts();
        if (timedOut > 0) {
            log.info("[通道目录] 超时同步收尾 count={}", timedOut);
        }
    }

    @Scheduled(fixedDelay = 100)
    public void drainKeepalive() {
        keepaliveNotifyMessageHandler.drain();
    }

    @Scheduled(initialDelay = 5_000, fixedDelay = 5_000)
    public void sweepSipSubscribe() {
        sipSubscribe.sweep();
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    public void sweepExpiredSubscribes() {
        subscribeHolder.sweepExpired();
    }

    @Scheduled(initialDelay = 10_000, fixedDelay = 10_000)
    public void sweepCascadeSendAckTimeout() {
        cascadeSendService.sweepAckTimeout();
    }

    @Scheduled(initialDelay = 10_000, fixedDelay = 10_000)
    public void sweepBroadcastAckTimeout() {
        gbBroadcastService.sweepAckTimeout();
    }

    @Scheduled(cron = "0 30 3 * * ?", zone = "Asia/Shanghai")
    public void cleanExpiredPositions() {
        int retentionDays = gb28181Properties.getPosition().getRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        int deleted = mobilePositionService.cleanExpired(retentionDays);
        log.info("[移动位置] 过期轨迹清理完成 retentionDays={} 删除条数={}", retentionDays, deleted);
    }
}
