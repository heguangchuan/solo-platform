package com.solo.gb28181.processor.request.message.notify;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.event.position.DevicePosition;
import com.solo.gb28181.event.position.PositionEvent;
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
public class MobilePositionNotifyMessageHandler extends SipRequestProcessorBase implements MessageHandler {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public String getRootType() {
        return "Notify";
    }

    @Override
    public String getCmdType() {
        return "MobilePosition";
    }

    @Override
    public void handleForDevice(RequestEvent event, GbDevice device, Element root) {
        DevicePosition position = parse(root);
        if (position == null) {
            return;
        }
        // 报文 DeviceID 和 SIP From 不一致时信 SIP 头
        position.setDeviceId(device.getDeviceId());
        publishEvent(position);
    }

    @Override
    public void handleForPlatform(RequestEvent event, GbParentPlatform platform, Element root) {
        DevicePosition position = parse(root);
        if (position == null) {
            return;
        }
        position.setDeviceId(platform.getServerGbId());
        publishEvent(position);
    }


    private DevicePosition parse(Element root) {
        DevicePosition position = new DevicePosition();

        // DeviceID 在国标定位中是设备 ID（与 Alarm 不同，Alarm 的 DeviceID 是通道）
        position.setDeviceId(XmlUtils.childText(root, "DeviceID"));

        // 没时间的轨迹点没法用
        String time = XmlUtils.childText(root, "Time");
        if (time == null || time.isEmpty()) {
            log.warn("[移动位置] 缺少 Time，跳过 deviceId={}", position.getDeviceId());
            return null;
        }
        LocalDateTime parsed = parseTime(time);
        if (parsed == null) {
            log.warn("[移动位置] Time 解析失败 deviceId={} time={}", position.getDeviceId(), time);
            return null;
        }
        position.setTime(parsed);

        position.setLongitude(parseDouble(XmlUtils.childText(root, "Longitude")));
        position.setLatitude(parseDouble(XmlUtils.childText(root, "Latitude")));

        position.setSpeed(parseDouble(XmlUtils.childText(root, "Speed")));
        position.setDirection(parseDouble(XmlUtils.childText(root, "Direction")));
        position.setAltitude(parseDouble(XmlUtils.childText(root, "Altitude")));

        position.setReceiveTime(LocalDateTime.now());
        return position;
    }

    private static Double parseDouble(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
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


    private void publishEvent(DevicePosition position) {
        try {
            eventPublisher.publishEvent(new PositionEvent(this, position));
        } catch (Exception e) {
            log.error("[移动位置] 发布 PositionEvent 失败 deviceId={} err={}",
                    position.getDeviceId(), e.getMessage());
        }
    }
}
