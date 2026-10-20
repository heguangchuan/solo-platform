package com.solo.gb28181.event.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.processor.request.NotifyFanoutSupport;
import com.solo.gb28181.processor.request.SubscribeHolder;
import com.solo.gb28181.processor.request.SubscribeInfo;
import com.solo.gb28181.processor.request.message.response.CatalogDataCache;
import com.solo.gb28181.subscribe.DeviceCatalogSubscribeManager;
import com.solo.gb28181.transmit.cmd.PlatformCommander;
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

    private final GbDeviceMapper deviceMapper;
    private final GbDeviceChannelMapper channelMapper;
    private final CatalogDataCache catalogDataCache;
    private final DeviceCatalogSubscribeManager catalogSubscribeManager;
    private final SubscribeHolder subscribeHolder;
    private final NotifyFanoutSupport fanout;
    private final PlatformCommander platformCommander;

    @Async("taskExecutor")
    @EventListener
    public void onDeviceOffline(DeviceOfflineEvent event) {

        catalogDataCache.cancel(event.getDeviceId());

        // 2) 停止目录订阅
        catalogSubscribeManager.stop(event.getDeviceId());

        // 3) 通道置 OFF
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, event.getDeviceId()));
        if (device == null) {
            return;
        }

        int updated = channelMapper.update(null, new LambdaUpdateWrapper<GbDeviceChannel>()
                .eq(GbDeviceChannel::getDeviceId, device.getDeviceId())
                .set(GbDeviceChannel::getStatus, "OFF"));
        if (updated > 0) {
            log.info("[设备离线] 通道批量置 OFF deviceId={} reason={}", event.getDeviceId(), event.getReason());
        }

        // 级联 NOTIFY：向订阅了该设备的上级推单设备 Catalog NOTIFY
        notifyStatus(device);
    }

    private void notifyStatus(GbDevice device) {
        for (SubscribeInfo sub : subscribeHolder.listByCmdType("Catalog")) {
            if (sub.getDeviceId() == null || sub.getDeviceId().isEmpty()) {
                continue;
            }
            if (!sub.getDeviceId().equals(device.getDeviceId())) {
                continue;
            }
            GbParentPlatform platform = fanout.platformOf(sub);
            if (platform == null) {
                continue;
            }
            String sn = sub.getSn() != null ? sub.getSn() : String.valueOf(PlatformCommander.nextSn());
            platformCommander.sendNotifyCatalogItem(platform, device, sn);
        }
    }
}
