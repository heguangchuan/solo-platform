package com.solo.gb28181.processor.request.message.notify;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.event.alarm.AlarmEvent;
import com.solo.gb28181.event.alarm.DeviceAlarm;
import com.solo.gb28181.event.alarm.DeviceAlarmMethod;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.processor.request.message.MessageHandler;
import com.solo.gb28181.utils.XmlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import java.time.LocalDateTime;

// 200 OK 已在 MessageRequestProcessor 回过，这里别再 responseAck，重复回包容易把设备搞糊涂。
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmNotifyMessageHandler extends SipRequestProcessorBase implements MessageHandler {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public String getRootType() {
        return "Notify";
    }

    @Override
    public String getCmdType() {
        return "Alarm";
    }

    @Override
    public void handleForDevice(RequestEvent event, GbDevice device, Element root) {
        DeviceAlarm alarm = parse(root);
        if (alarm == null) {
            return;
        }
        alarm.setDeviceId(device.getDeviceId());
        publishEvent(alarm);
    }

    @Override
    public void handleForPlatform(RequestEvent event, GbParentPlatform platform, Element root) {
        DeviceAlarm alarm = parse(root);
        if (alarm == null) {
            return;
        }
        alarm.setDeviceId(platform.getServerGbId());
        publishEvent(alarm);
    }


    private DeviceAlarm parse(Element root) {
        DeviceAlarm alarm = new DeviceAlarm();

        String channelId = XmlUtils.childText(root, "DeviceID");
        alarm.setChannelId(channelId);

        alarm.setAlarmPriority(XmlUtils.childText(root, "AlarmPriority"));

        String method = XmlUtils.childText(root, "AlarmMethod");
        alarm.setAlarmMethod(method);
        alarm.setAlarmMethodDesc(resolveMethodDesc(method));

        // 没时间的报警没法用
        String time = XmlUtils.childText(root, "AlarmTime");
        if (time == null || time.isEmpty()) {
            log.warn("[报警通知] 缺少 AlarmTime，跳过 channelId={}", channelId);
            return null;
        }
        LocalDateTime parsed = parseTime(time);
        if (parsed == null) {
            log.warn("[报警通知] AlarmTime 解析失败 channelId={} time={}", channelId, time);
            return null;
        }
        alarm.setAlarmTime(parsed);

        alarm.setAlarmDescription(XmlUtils.childText(root, "AlarmDescription"));

        String lng = XmlUtils.childText(root, "Longitude");
        String lat = XmlUtils.childText(root, "Latitude");
        if (lng != null && !lng.isEmpty()) {
            try {
                alarm.setLongitude(Double.parseDouble(lng));
            } catch (NumberFormatException ignored) {
                // 部分厂商经纬度字段塞了非法字符（厂商扩展），不阻塞整条报警
            }
        }
        if (lat != null && !lat.isEmpty()) {
            try {
                alarm.setLatitude(Double.parseDouble(lat));
            } catch (NumberFormatException ignored) {
            }
        }

        Element info = root.element("Info");
        if (info != null) {
            alarm.setAlarmType(XmlUtils.childText(info, "AlarmType"));
        }

        alarm.setReceiveTime(LocalDateTime.now());
        return alarm;
    }

    private String resolveMethodDesc(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c < '1' || c > '7') {
                continue;
            }
            int n = c - '0';
            DeviceAlarmMethod m = DeviceAlarmMethod.typeOf(n);
            if (m != null) {
                if (sb.length() > 0) {
                    sb.append(" / ");
                }
                sb.append(m.getDesc());
            }
        }
        return sb.toString();
    }

    // 海康/大华可能带毫秒、时区，甚至用空格替 T，能剥就先剥。
    private LocalDateTime parseTime(String s) {
        try {
            return LocalDateTime.parse(s);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(s.substring(0, Math.min(s.length(), 23)));
        } catch (Exception ignored) {
        }
        // 兼容不带 T 分隔符的厂商扩展：YYYY-MM-DD HH:mm:ss
        try {
            String normalized = s.replace(' ', 'T');
            if (normalized.length() >= 19) {
                return LocalDateTime.parse(normalized.substring(0, 19));
            }
        } catch (Exception ignored) {
        }
        return null;
    }


    private void publishEvent(DeviceAlarm alarm) {
        try {
            eventPublisher.publishEvent(new AlarmEvent(this, alarm));
        } catch (Exception e) {
            log.error("[报警通知] 发布 AlarmEvent 失败 channelId={} err={}",
                    alarm.getChannelId(), e.getMessage());
        }
    }
}
