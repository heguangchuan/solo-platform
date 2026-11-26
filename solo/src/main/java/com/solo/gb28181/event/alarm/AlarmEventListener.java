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

@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmEventListener {

    private final SubscribeHolder subscribeHolder;
    private final NotifyFanoutSupport fanout;
    private final PlatformCommander platformCommander;

    @Async("gb28181BizExecutor")
    @EventListener
    public void onAlarm(AlarmEvent event) {
        DeviceAlarm a = event.getAlarm();
        // 用 WARN 级（不是 ERROR）：报警是业务事件，不是系统错误；但希望它能在告警面板冒出来
        // 报警是业务事件不是系统异常，所以用 WARN 不用 ERROR。
        log.warn("[设备报警] deviceId={} channelId={} priority={} method={} ({}) time={} desc={} type={} pos=({}, {})",
                a.getDeviceId(),
                a.getChannelId(),
                priorityDesc(a.getAlarmPriority()),
                a.getAlarmMethod(),
                nullToEmpty(a.getAlarmMethodDesc()),
                a.getAlarmTime(),
                nullToEmpty(a.getAlarmDescription()),
                nullToEmpty(a.getAlarmType()),
                a.getLongitude(),
                a.getLatitude());

        notifyAlarm(a);
    }

    private void notifyAlarm(DeviceAlarm alarm) {
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

    private static String priorityDesc(String p) {
        if (p == null || p.isEmpty()) return "?";
        return switch (p) {
            case "1" -> "一级";
            case "2" -> "二级";
            case "3" -> "三级";
            case "4" -> "四级";
            default -> "未知(" + p + ")";
        };
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
