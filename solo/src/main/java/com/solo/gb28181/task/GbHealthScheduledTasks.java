package com.solo.gb28181.task;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.processor.request.SubscribeHolder;
import com.solo.gb28181.processor.request.message.notify.KeepaliveNotifyMessageHandler;
import com.solo.gb28181.service.GbMobilePositionService;
import com.solo.gb28181.service.impl.CascadeSendService;
import com.solo.gb28181.sip.SipSubscribe;
import com.solo.gb28181.support.CatalogSyncStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * GB 核心健康/订阅/位置维护定时任务（聚合器风格：本类只持有 {@link Scheduled} 触发点，
 * 业务全部委托给对应 worker/service，便于集中管理调度周期）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GbHealthScheduledTasks {

    private final DeviceOfflineDetector deviceOfflineDetector;
    private final KeepaliveNotifyMessageHandler keepaliveNotifyMessageHandler;
    private final SipSubscribe sipSubscribe;
    private final SubscribeHolder subscribeHolder;
    private final CascadeSendService cascadeSendService;
    private final GbMobilePositionService mobilePositionService;
    private final CatalogSyncStatusService catalogSyncStatusService;
    private final Gb28181Properties gb28181Properties;

    /** 设备离线检测：每 30s 扫库比对 Redis 在线 key，并清理目录分片缓存 */
    @Scheduled(fixedDelay = 30_000)
    public void detectDeviceOffline() {
        deviceOfflineDetector.detect();
    }

    /** 通道目录同步无响应超时兜底：每 30s 把僵尸 RUNNING 状态强制收尾为 FAILED */
    @Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
    public void sweepCatalogSyncTimeouts() {
        int timedOut = catalogSyncStatusService.sweepRunningTimeouts();
        if (timedOut > 0) {
            log.info("[通道目录] 超时同步收尾 count={}", timedOut);
        }
    }

    /** 心跳队列批处理：每 100ms 从队列取一批落库，削峰避免打爆 DB */
    @Scheduled(fixedDelay = 100)
    public void drainKeepalive() {
        keepaliveNotifyMessageHandler.drain();
    }

    /** SIP 响应订阅超时回收：每 5s 扫一次，移除超时订阅并触发 onTimeout */
    @Scheduled(initialDelay = 5_000, fixedDelay = 5_000)
    public void sweepSipSubscribe() {
        sipSubscribe.sweep();
    }

    /** 国标订阅（Catalog/MobilePosition/Alarm）过期清理：每 5 分钟扫一次 */
    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    public void sweepExpiredSubscribes() {
        subscribeHolder.sweepExpired();
    }

    /** 级联被点播：回 200 OK 后等上级 ACK 超时的会话回收（每 10s 扫一次） */
    @Scheduled(initialDelay = 10_000, fixedDelay = 10_000)
    public void sweepCascadeSendAckTimeout() {
        cascadeSendService.sweepAckTimeout();
    }

    /** GPS 轨迹过期清理：每天凌晨 03:30 按保留天数删除旧轨迹点（retentionDays<=0 不清理） */
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
