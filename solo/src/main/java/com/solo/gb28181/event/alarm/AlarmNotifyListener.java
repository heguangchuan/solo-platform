package com.solo.gb28181.event.alarm;

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
 * 报警级联 NOTIFY 推送。
 * <p>按设备或通道两个维度匹配订阅，向匹配的上级平台推 Alarm NOTIFY。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmNotifyListener {

    private final SubscribeHolder subscribeHolder;
    private final NotifyFanoutSupport fanout;
    private final PlatformCommander platformCommander;

    @Async("gb28181BizExecutor")
    @EventListener
    public void onAlarm(AlarmEvent event) {
        DeviceAlarm alarm = event.getAlarm();
        if (alarm == null) {
            return;
        }
        String alarmDeviceId = alarm.getDeviceId();
        String alarmChannelId = alarm.getChannelId();
        for (SubscribeInfo sub : subscribeHolder.listByCmdType("Alarm")) {
            boolean hit = NotifyFanoutSupport.matches(sub, alarmDeviceId)
                    || (alarmChannelId != null && NotifyFanoutSupport.matches(sub, alarmChannelId));
            if (!hit) {
                continue;
            }
            GbParentPlatform platform = fanout.platformOf(sub);
            if (platform == null) {
                continue;
            }
            String sn = sub.getSn() != null ? sub.getSn() : String.valueOf(PlatformCommander.nextSn());
            platformCommander.sendNotifyAlarm(platform, alarm, sn);
        }
    }
}
