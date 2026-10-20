package com.solo.gb28181.event.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.processor.request.message.response.CatalogDataCache;
import com.solo.gb28181.subscribe.DeviceCatalogSubscribeManager;
import com.solo.gb28181.support.CatalogSyncStatusService;
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

    @Async("gb28181BizExecutor")
    @EventListener
    public void onDeviceOnline(DeviceOnlineEvent event) {
        // 1) 先清残留的 in-flight sync：老 sync 占着 SN 不让位，新 sync 来了会被 startSync 丢
        catalogDataCache.cancel(event.getDeviceId());

        // 2) 走 Redis 锁，5 分钟内同设备重复触发直接跳过
        if (!catalogSyncStatusService.tryAcquire(event.getDeviceId())) {
            log.info("[设备上线] 同步锁被占用，跳过本次触发 deviceId={} firstTime={}",
                    event.getDeviceId(), event.isFirstTime());
            return;
        }
        // 锁由 CatalogSyncProgressEvent 在 sync 收尾（非 RUNNING 状态）时自动 release

        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, event.getDeviceId()));
        if (device == null) {
            return;
        }
        try {
            sipCommander.queryDeviceInfo(device);
            Thread.sleep(500);
            sipCommander.queryCatalog(device);
            log.info("[设备上线] 触发设备信息和通道目录同步 deviceId={} firstTime={}", event.getDeviceId(), event.isFirstTime());
            // 3) 启动目录订阅（如果设备配置了订阅周期）
            catalogSubscribeManager.start(event.getDeviceId());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[设备上线] 同步触发失败 deviceId={}", event.getDeviceId(), e);
        }
    }
}
