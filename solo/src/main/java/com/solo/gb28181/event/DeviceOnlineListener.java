package com.solo.gb28181.event;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.transmit.cmd.SipCommander;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 设备上线监听器
 * <p>设备注册成功后异步拉取 DeviceInfo 和 Catalog；
 * 间隔 500ms 避免设备瞬时处理压力，且 DeviceInfo 通常先到便于先填厂商型号。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceOnlineListener {

    private final GbDeviceService deviceService;
    private final SipCommander sipCommander;

    @Async("gb28181BizExecutor")
    @EventListener
    public void onDeviceOnline(DeviceOnlineEvent event) {
        GbDevice device = deviceService.lambdaQuery().eq(GbDevice::getDeviceId, event.getDeviceId()).one();
        if (device == null) return;
        try {
            sipCommander.queryDeviceInfo(device);
            Thread.sleep(500);
            sipCommander.queryCatalog(device);
            log.info("[设备上线] 触发设备信息和通道目录同步 deviceId={} firstTime={}", event.getDeviceId(), event.isFirstTime());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[设备上线] 同步触发失败 deviceId={}", event.getDeviceId(), e);
        }
    }
}
