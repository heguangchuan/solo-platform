package com.solo.gb28181.event.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.processor.request.NotifyFanoutSupport;
import com.solo.gb28181.processor.request.SubscribeHolder;
import com.solo.gb28181.processor.request.SubscribeInfo;
import com.solo.gb28181.support.CatalogSyncProgress;
import com.solo.gb28181.transmit.cmd.PlatformCommander;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * <p>目录同步到终态后，向订阅了该设备的上级平台推该设备的通道列表；级联订阅（deviceId 空）不在这里处理。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogSyncProgressEventListener {

    private final SubscribeHolder subscribeHolder;
    private final NotifyFanoutSupport fanout;
    private final GbDeviceChannelMapper channelMapper;
    private final PlatformCommander platformCommander;

    @Async("gb28181BizExecutor")
    @EventListener
    public void onCatalogSynced(CatalogSyncProgressEvent event) {
        CatalogSyncProgress progress = event.getProgress();
        if (progress == null) {
            return;
        }
        String status = progress.getStatus();
        if (!CatalogSyncProgress.STATUS_SUCCESS.equals(status)
                && !CatalogSyncProgress.STATUS_FAILED.equals(status)) {
            return;
        }
        String deviceId = progress.getDeviceId();
        List<GbDeviceChannel> channels = channelMapper.selectList(
                new LambdaQueryWrapper<GbDeviceChannel>()
                        .eq(GbDeviceChannel::getDeviceId, deviceId)
                        .orderByDesc(GbDeviceChannel::getParental)
                        .orderByAsc(GbDeviceChannel::getChannelId));

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
            platformCommander.sendNotifyCatalogList(platform, deviceId, channels, sn);
        }
    }
}
