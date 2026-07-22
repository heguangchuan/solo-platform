package com.solo.onvif.task;

import com.solo.onvif.entity.OnvifDevice;
import com.solo.onvif.service.OnvifDeviceService;
import com.solo.onvif.service.impl.OnvifDeviceServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ONVIF 设备在线状态定时探测
 * <p>与国标"SIP 交互改状态"路径不同，ONVIF 靠平台主动探测：每 30s 遍历库里所有设备，
 * TCP ping → SOAP 探测 → 回写 status / error_msg。</p>
 * <p>用 GB 已有的 taskExecutor 线程池并发，避免设备多时串行探测把周期拖长。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnvifDeviceStatusTask {

    private final OnvifDeviceService deviceService;
    private final ThreadPoolTaskExecutor taskExecutor;

    @Scheduled(fixedDelay = 30_000L, initialDelay = 10_000L)
    public void checkAll() {
        List<OnvifDevice> all = deviceService.list();
        if (all.isEmpty()) {
            return;
        }
        log.debug("[ONVIF探测] 定时探测开始，共 {} 台", all.size());

        for (OnvifDevice device : all) {
            taskExecutor.execute(() -> {
                try {
                    deviceService.probeInternal(device);
                } catch (Exception e) {
                    log.warn("[ONVIF探测] 定时探测异常 id={} host={} err={}",
                            device.getId(), device.getHost(), e.getMessage());
                }
            });
        }
    }
}
