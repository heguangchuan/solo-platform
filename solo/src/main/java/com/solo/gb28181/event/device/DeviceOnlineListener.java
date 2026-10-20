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

/**
 * 设备上线监听器
 * <p>设备注册成功后异步拉取 DeviceInfo 和 Catalog；
 * 间隔 500ms 避免设备瞬时处理压力，且 DeviceInfo 通常先到便于先填厂商型号。</p>
 *
 * <h3>防闪断</h3>
 * <p>设备闪断重连会反复触发 {@link DeviceOnlineEvent}。本类做两道防护：</p>
 * <ol>
 *   <li>先调 {@link CatalogDataCache#cancel} 抹掉可能残留的 in-flight 上下文（防老 sync 占 SN）</li>
 *   <li>走 {@link CatalogSyncStatusService#tryAcquire} Redis SETNX 锁（5 min TTL），
 *       挡住"5 分钟内重复触发"——锁由 {@code CatalogSyncProgressEvent} 非 RUNNING 状态时自动释放</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceOnlineListener {

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
        // 1) 先清残留的 in-flight sync：老 sync 占着 SN 不让位，新 sync 来了会被 startSync 丢
        catalogDataCache.cancel(event.getDeviceId());

        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, event.getDeviceId()));
        if (device == null) {
            return;
        }

        // 2) DeviceInfo 查询不受 Catalog 同步锁影响，每次上线都发
        try {
            sipCommander.queryDeviceInfo(device);
            Thread.sleep(500);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[设备上线] DeviceInfo 查询失败 deviceId={}", event.getDeviceId(), e);
        }

        // 3) Catalog 同步走 Redis 锁，5 分钟内重复触发跳过
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

        // 4) 启动目录订阅（不受 Catalog 锁影响，每次上线都尝试）
        catalogSubscribeManager.start(event.getDeviceId());

        // 5) 级联 NOTIFY：向订阅了该设备的上级推单设备 Catalog NOTIFY
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
