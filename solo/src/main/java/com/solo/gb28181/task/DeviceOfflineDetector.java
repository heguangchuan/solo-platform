package com.solo.gb28181.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.constant.GbRedisKeys;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.event.DeviceOfflineEvent;
import com.solo.gb28181.processor.request.message.response.CatalogDataCache;
import com.solo.gb28181.service.GbDeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 设备离线检测
 * <p>Redis 在线 key TTL 是真权威；定时扫库 status=1 的设备，逐个检查 Redis key 是否存在：</p>
 * <ul>
 *   <li>不存在 → 设置 status=0 + 发 {@link DeviceOfflineEvent}</li>
 * </ul>
 * <p>同时清理 {@link CatalogDataCache} 里超时的分片缓存。</p>
 * <p>调度由 {@link GbScheduledTasks} 每 30s 触发。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceOfflineDetector {

    private final GbDeviceService deviceService;
    private final StringRedisTemplate redisTemplate;
    private final CatalogDataCache catalogDataCache;
    private final ApplicationEventPublisher eventPublisher;

    public void detect() {
        // 清理目录分片缓存中的僵尸 batch
        catalogDataCache.sweep();

        // 扫库找在线设备
        List<GbDevice> onlines = deviceService.list(new LambdaQueryWrapper<GbDevice>()
                .eq(GbDevice::getStatus, 1));
        if (onlines.isEmpty()) return;

        int offlineCount = 0;
        for (GbDevice device : onlines) {
            Boolean exist = redisTemplate.hasKey(GbRedisKeys.DEVICE_ONLINE_PREFIX + device.getDeviceId());
            if (Boolean.TRUE.equals(exist)) continue;

            deviceService.offline(device.getDeviceId(), "心跳超时");
            eventPublisher.publishEvent(new DeviceOfflineEvent(this, device.getDeviceId(), "心跳超时"));
            offlineCount++;
        }
        if (offlineCount > 0) {
            log.info("[设备离线] 心跳超时清理 count={}", offlineCount);
        }
    }
}
