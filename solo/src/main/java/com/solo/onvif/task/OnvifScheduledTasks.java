package com.solo.onvif.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ONVIF 设备状态定时探测（聚合器风格）：仅持有 {@link Scheduled} 触发点，
 * 业务探测逻辑委托给 {@link OnvifDeviceStatusTask}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnvifScheduledTasks {

    private final OnvifDeviceStatusTask onvifDeviceStatusTask;

    /** 每 30s 遍历所有 ONVIF 设备并发探测（业务在 OnvifDeviceStatusTask） */
    @Scheduled(fixedDelay = 30_000L, initialDelay = 10_000L)
    public void checkAll() {
        onvifDeviceStatusTask.checkAll();
    }
}
