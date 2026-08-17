package com.solo.gb28181.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.processor.request.message.response.CatalogDataCache;
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

    @Async("taskExecutor")
    @EventListener
    public void onDeviceOffline(DeviceOfflineEvent event) {
        // 1) 取消 in-flight 的 catalog 同步（关键！避免老 sync 占着 SN 不让位，
        //    导致设备重新注册后的新 sync 被 startSync 丢成 null）。
        //    cancel 不 flush 部分数据——下线的设备回得不完整，flush 反而把脏数据落地；
        //    留待设备重新注册后由新 sync 拉完整目录覆盖。
        catalogDataCache.cancel(event.getDeviceId());

        // 2) 通道置 OFF
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, event.getDeviceId()));
        if (device == null) return;

        int updated = channelMapper.update(null, new LambdaUpdateWrapper<GbDeviceChannel>()
                .eq(GbDeviceChannel::getDeviceId, device.getDeviceId())
                .set(GbDeviceChannel::getStatus, "OFF"));
        if (updated > 0) {
            log.info("[设备离线] 通道批量置 OFF deviceId={} reason={}", event.getDeviceId(), event.getReason());
        }
    }
}
