package com.solo.gb28181.task;

import com.solo.gb28181.processor.request.message.notify.KeepaliveNotifyMessageHandler;
import com.solo.gb28181.sip.SipSubscribe;
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
}
