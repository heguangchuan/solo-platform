package com.solo.gb28181.event.position;

import com.solo.gb28181.entity.GbParentPlatform;
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
 * 位置级联 NOTIFY 推送。
 * <p>向订阅了该设备（或订阅全部）的上级平台推 MobilePosition NOTIFY。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PositionNotifyListener {

    private final SubscribeHolder subscribeHolder;
    private final NotifyFanoutSupport fanout;
    private final PlatformCommander platformCommander;

    @Async("gb28181BizExecutor")
    @EventListener
    public void onPosition(PositionEvent event) {
        DevicePosition pos = event.getPosition();
        if (pos == null || pos.getDeviceId() == null) {
            return;
        }
        for (SubscribeInfo sub : subscribeHolder.listByCmdType("MobilePosition")) {
            if (!NotifyFanoutSupport.matches(sub, pos.getDeviceId())) {
                continue;
            }
            GbParentPlatform platform = fanout.platformOf(sub);
            if (platform == null) {
                continue;
            }
            String sn = sub.getSn() != null ? sub.getSn() : String.valueOf(PlatformCommander.nextSn());
            platformCommander.sendNotifyMobilePosition(platform, pos, sn);
        }
    }
}
