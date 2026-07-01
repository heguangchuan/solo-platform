package com.solo.gb28181.event;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 设备离线监听器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceOfflineListener {

    private final GbDeviceService deviceService;
    private final GbDeviceChannelService channelService;

    @Async("taskExecutor")
    @EventListener
    public void onDeviceOffline(DeviceOfflineEvent event) {
        GbDevice device = deviceService.lambdaQuery().eq(GbDevice::getDeviceId, event.getDeviceId()).one();
        if (device == null) return;

        boolean updated = channelService.lambdaUpdate()
                .eq(GbDeviceChannel::getDeviceDbId, device.getId())
                .set(GbDeviceChannel::getStatus, "OFF")
                .update();
        if (updated) {
            log.info("[设备离线] 通道批量置 OFF deviceId={} reason={}", event.getDeviceId(), event.getReason());
        }
    }
}
