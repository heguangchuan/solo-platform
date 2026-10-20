package com.solo.gb28181.event.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.processor.request.NotifyFanoutSupport;
import com.solo.gb28181.processor.request.SubscribeHolder;
import com.solo.gb28181.processor.request.SubscribeInfo;
import com.solo.gb28181.transmit.cmd.PlatformCommander;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 设备上下线级联 NOTIFY 推送。
 * <p>向订阅了该设备（deviceId 精确匹配）的上级平台推单设备 Catalog NOTIFY；级联订阅（deviceId 空）走 share 推送，不在这里处理。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceStatusNotifyListener {

    private final SubscribeHolder subscribeHolder;
    private final NotifyFanoutSupport fanout;
    private final GbDeviceMapper deviceMapper;
    private final PlatformCommander platformCommander;

    @Async("gb28181BizExecutor")
    @EventListener
    public void onDeviceOnline(DeviceOnlineEvent event) {
        notifyStatus(event.getDeviceId());
    }

    @Async("gb28181BizExecutor")
    @EventListener
    public void onDeviceOffline(DeviceOfflineEvent event) {
        notifyStatus(event.getDeviceId());
    }

    private void notifyStatus(String deviceId) {
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, deviceId));
        if (device == null) {
            return;
        }
        for (SubscribeInfo sub : subscribeHolder.listByCmdType("Catalog")) {
            if (sub.getDeviceId() == null || sub.getDeviceId().isEmpty()) {
                continue;
            }
            if (!sub.getDeviceId().equals(deviceId)) {
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
