package com.solo.gb28181.event;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.processor.request.message.response.CatalogDataCache;
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
    private final CatalogDataCache catalogDataCache;

    @Async("taskExecutor")
    @EventListener
    public void onDeviceOffline(DeviceOfflineEvent event) {
        // 1) 取消 in-flight 的 catalog 同步（关键！避免老 sync 占着 SN 不让位，
        //    导致设备重新注册后的新 sync 被 startSync 丢成 null）。
        //    cancel 不 flush 部分数据——下线的设备回得不完整，flush 反而把脏数据落地；
        //    留待设备重新注册后由新 sync 拉完整目录覆盖。
        catalogDataCache.cancel(event.getDeviceId());

        // 2) 通道置 OFF
        GbDevice device = deviceService.lambdaQuery().eq(GbDevice::getDeviceId, event.getDeviceId()).one();
        if (device == null) return;

        boolean updated = channelService.lambdaUpdate()
                .eq(GbDeviceChannel::getDeviceId, device.getDeviceId())
                .set(GbDeviceChannel::getStatus, "OFF")
                .update();
        if (updated) {
            log.info("[设备离线] 通道批量置 OFF deviceId={} reason={}", event.getDeviceId(), event.getReason());
        }
    }
}
