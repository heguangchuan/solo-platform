package com.solo.gb28181.event.catalog;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.processor.request.SubscribeHolder;
import com.solo.gb28181.processor.request.SubscribeInfo;
import com.solo.gb28181.service.CatalogShareService;
import com.solo.gb28181.service.GbParentPlatformService;
import com.solo.gb28181.transmit.cmd.PlatformCommander;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 目录共享关系变化级联 NOTIFY 推送。
 * <p>share 增删后，对活跃的级联订阅（deviceId 空 = 订阅整个平台目录）按最新共享通道推送。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShareChangedNotifyListener {

    private final SubscribeHolder subscribeHolder;
    private final GbParentPlatformService gbParentPlatformService;
    private final CatalogShareService catalogShareService;
    private final PlatformCommander platformCommander;
    private final Gb28181Properties properties;

    @Async("gb28181BizExecutor")
    @EventListener
    public void onShareChanged(ShareChangedEvent event) {
        Long platformId = event.getPlatformId();
        if (platformId == null) {
            return;
        }
        GbParentPlatform platform = gbParentPlatformService.getById(platformId);
        if (platform == null || !Integer.valueOf(1).equals(platform.getEnabled())) {
            return;
        }
        SubscribeInfo sub = subscribeHolder.getActive(platform.getServerGbId(), "Catalog").orElse(null);
        if (sub == null || sub.getDeviceId() != null && !sub.getDeviceId().isEmpty()) {
            return;
        }
        List<GbDeviceChannel> channels = catalogShareService.loadSharedChannels(platformId);
        if (channels == null) {
            channels = List.of();
        }
        platformCommander.sendNotifyCatalogList(platform, properties.getId(), channels,
                String.valueOf(PlatformCommander.nextSn()));
        log.info("[级联订阅] share 变化推送 上级平台:{} 共享通道:{}",
                platform.getServerGbId(), channels.size());
    }
}
