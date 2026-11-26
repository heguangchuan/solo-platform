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

@Slf4j
@Component
@RequiredArgsConstructor
public class PositionEventListener {

    private final SubscribeHolder subscribeHolder;
    private final NotifyFanoutSupport fanout;
    private final PlatformCommander platformCommander;

    @Async("gb28181BizExecutor")
    @EventListener
    public void onPosition(PositionEvent event) {
        DevicePosition p = event.getPosition();
        // 定位可能几秒一条，是常态不是告警，别用 WARN 刷告警面板。
        log.info("[移动位置] deviceId={} time={} pos=({}, {}) speed={} direction={} altitude={}",
                p.getDeviceId(),
                p.getTime(),
                p.getLongitude(),
                p.getLatitude(),
                nullToEmpty(p.getSpeed()),
                nullToEmpty(p.getDirection()),
                nullToEmpty(p.getAltitude()));

        notifyPosition(p);
    }

    private void notifyPosition(DevicePosition pos) {
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

    private static String nullToEmpty(Double d) {
        return d == null ? "" : String.valueOf(d);
    }
}
