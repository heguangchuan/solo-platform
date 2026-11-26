package com.solo.gb28181.event.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.processor.request.NotifyFanoutSupport;
import com.solo.gb28181.processor.request.SubscribeHolder;
import com.solo.gb28181.processor.request.SubscribeInfo;
import com.solo.gb28181.processor.request.message.response.CatalogDataCache;
import com.solo.gb28181.subscribe.DeviceCatalogSubscribeManager;
import com.solo.gb28181.support.CatalogSyncStatusService;
import com.solo.gb28181.transmit.cmd.PlatformCommander;
import com.solo.gb28181.transmit.cmd.SipCommander;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// 设备闪断会反复触发上线，Catalog 用 Redis 锁挡住；DeviceInfo 和订阅不受这把锁影响。
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceOnlineEventListener {

    private final GbDeviceMapper deviceMapper;
    private final SipCommander sipCommander;
    private final CatalogDataCache catalogDataCache;
    private final CatalogSyncStatusService catalogSyncStatusService;
    private final DeviceCatalogSubscribeManager catalogSubscribeManager;
    private final SubscribeHolder subscribeHolder;
    private final NotifyFanoutSupport fanout;
    private final PlatformCommander platformCommander;

    @Async("gb28181BizExecutor")
    @EventListener
    public void onDeviceOnline(DeviceOnlineEvent event) {
        // 先清老同步，免得残留上下文还占着 SN
        catalogDataCache.cancel(event.getDeviceId());

        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, event.getDeviceId()));
        if (device == null) {
            return;
        }

        try {
            sipCommander.queryDeviceInfo(device);
            Thread.sleep(500);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[设备上线] DeviceInfo 查询失败 deviceId={}", event.getDeviceId(), e);
        }

        boolean catalogStarted = false;
        if (catalogSyncStatusService.tryAcquire(event.getDeviceId())) {
            try {
                sipCommander.queryCatalog(device);
                catalogStarted = true;
                log.info("[设备上线] 触发通道目录同步 deviceId={} firstTime={}",
                        event.getDeviceId(), event.isFirstTime());
            } catch (Exception e) {
                log.error("[设备上线] Catalog 同步触发失败 deviceId={}", event.getDeviceId(), e);
                catalogSyncStatusService.release(event.getDeviceId());
            }
        } else {
            log.info("[设备上线] 同步锁被占用，跳过 Catalog 同步 deviceId={} firstTime={}",
                    event.getDeviceId(), event.isFirstTime());
        }

        catalogSubscribeManager.start(event.getDeviceId());

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
