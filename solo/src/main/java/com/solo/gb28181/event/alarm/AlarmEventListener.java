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
 * 设备报警监听器
 *
 * <h3>当前职责</h3>
 * <p>仅按统一格式日志输出报警内容，<b>不入库</b>（按用户要求）。
 * 海康 / 大华 / 宇视现场触发时，运维 grep 关键字 {@code [设备报警]} 即可定位。</p>
 *
 * <h3>线程</h3>
 * <p>挂 {@code @Async("gb28181BizExecutor")} 异步处理：SIP 监听线程解析完 XML 后立即
 * 继续处理下一条，报警日志落盘放到业务线程池，避免日志量突增时阻塞 SIP 接收。</p>
 *
 * <h3>扩展点</h3>
 * <p>要加落库 / 推 WebSocket / 发企微等动作，<b>新写一个监听器</b>订阅 {@link AlarmEvent} 即可，
 * 不要在本类里堆 if 分支——报警事件订阅者多对一干净，多种处理各起一个 listener。</p>
 */
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

        // 级联 NOTIFY：向订阅了该设备/通道的上级推 Alarm NOTIFY
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
