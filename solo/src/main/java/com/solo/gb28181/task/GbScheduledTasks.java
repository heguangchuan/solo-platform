package com.solo.gb28181.task;

import com.solo.gb28181.processor.request.message.notify.KeepaliveNotifyMessageHandler;
import com.solo.gb28181.service.PlatformRegisterService;
import com.solo.gb28181.sip.SipSubscribe;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 国标模块所有定时任务的统一入口
 * <p>把原先分散在各业务类里的调度逻辑集中到 task 目录，方便统一管理调度周期。
 * 各业务类只保留纯业务方法，由本类持有 {@link Scheduled} 触发点后逐个委派：</p>
 * <ul>
 *   <li>每 30s → {@link DeviceOfflineDetector#detect()} 设备离线检测 + 目录分片缓存清理</li>
 *   <li>每 100ms → {@link KeepaliveNotifyMessageHandler#drain()} 心跳队列批处理</li>
 *   <li>每 5s → {@link SipSubscribe#sweep()} SIP 响应订阅超时回收</li>
 *   <li>启动后 10s → 级联首次注册；每 60s → 级联心跳 + 离线重注册；每 30min → 注册刷新</li>
 * </ul>
 * <p>调度总开关 {@code @EnableScheduling} 在 SoloApplication 上开启。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GbScheduledTasks {

    private final DeviceOfflineDetector deviceOfflineDetector;
    private final KeepaliveNotifyMessageHandler keepaliveNotifyMessageHandler;
    private final SipSubscribe sipSubscribe;
    private final PlatformRegisterService platformRegisterService;

    /** 设备离线检测：每 30s 扫库比对 Redis 在线 key，并清理目录分片缓存 */
    @Scheduled(fixedDelay = 30_000)
    public void detectDeviceOffline() {
        deviceOfflineDetector.detect();
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

    // ============================================================
    // 级联下级侧：向上级平台注册 + 保活（S6.b-1）
    // ============================================================

    /** 启动后 10s 对所有启用的上级平台发起首次注册（仅执行一次） */
    @Scheduled(initialDelay = 10_000, fixedDelay = Long.MAX_VALUE)
    public void cascadeRegisterOnStartup() {
        platformRegisterService.registerAll();
    }

    /** 级联心跳 + 离线重注册：每 60s 一次（在线发心跳，离线重试注册） */
    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void cascadeKeepalive() {
        platformRegisterService.keepaliveAll();
        platformRegisterService.registerOffline();
    }

    /** 级联注册刷新：每 30min 重发一次注册，防止 expires 过期 */
    @Scheduled(initialDelay = 1_800_000, fixedDelay = 1_800_000)
    public void cascadeRefresh() {
        platformRegisterService.refreshAll();
    }

    /** 关停时对所有在线上级发送注销 */
    @PreDestroy
    public void cascadeUnregister() {
        try {
            platformRegisterService.unregisterAll();
        } catch (Exception e) {
            log.warn("[级联注册] 关停注销异常: {}", e.getMessage());
        }
    }
}
