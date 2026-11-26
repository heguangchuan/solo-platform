package com.solo.gb28181.event.alarm;

import com.solo.gb28181.service.GbDeviceAlarmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 报警落库与 SIP 主链路隔离，数据库异常不影响其它监听器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmPersistenceListener {

    private final GbDeviceAlarmService alarmService;

    @Async("gb28181BizExecutor")
    @EventListener
    public void onAlarm(AlarmEvent event) {
        DeviceAlarm source = event.getAlarm();
        try {
            alarmService.saveFromEvent(source);
        } catch (Exception e) {
            log.error("[报警中心] 报警落库失败 deviceId={} channelId={}",
                    source.getDeviceId(), source.getChannelId(), e);
        }
    }
}
