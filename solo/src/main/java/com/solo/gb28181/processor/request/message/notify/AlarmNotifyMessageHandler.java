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

/**
 * 国标报警通知处理（Notify / CmdType = Alarm）
 *
 * <h3>报文结构</h3>
 * <pre>
 * &lt;Notify&gt;
 *   &lt;CmdType&gt;Alarm&lt;/CmdType&gt;
 *   &lt;SN&gt;...&lt;/SN&gt;
 *   &lt;DeviceID&gt;通道国标ID&lt;/DeviceID&gt;     &lt;!-- 国标规定设备报警中 DeviceID 字段是通道 ID --&gt;
 *   &lt;AlarmPriority&gt;1&lt;/AlarmPriority&gt;     &lt;!-- 1~4 --&gt;
 *   &lt;AlarmMethod&gt;5&lt;/AlarmMethod&gt;         &lt;!-- 可组合: 1..7 --&gt;
 *   &lt;AlarmTime&gt;2024-01-15T14:30:00&lt;/AlarmTime&gt;
 *   &lt;AlarmDescription&gt;...&lt;/AlarmDescription&gt;
 *   &lt;Longitude&gt;...&lt;/Longitude&gt;          &lt;!-- 可选 --&gt;
 *   &lt;Latitude&gt;...&lt;/Latitude&gt;            &lt;!-- 可选 --&gt;
 *   &lt;Info&gt;                              &lt;!-- 可选，alarmMethod=2/5/6 时携带 --&gt;
 *     &lt;AlarmType&gt;1&lt;/AlarmType&gt;
 *   &lt;/Info&gt;
 * &lt;/Notify&gt;
 * </pre>
 *
 * <h3>职责</h3>
 * <ol>
 *   <li>解析 XML 字段填进 {@link DeviceAlarm} DTO</li>
 *   <li>发布 {@link AlarmEvent}，由 {@link com.solo.gb28181.event.alarm.AlarmEventListener} 异步处理</li>
 *   <li>不落库（按当前 Sprint 决定），不推 SSE / 企微（后续 Sprint 补）</li>
 * </ol>
 *
 * <h3>来源分支</h3>
 * <ul>
 *   <li>下级设备上报：{@link #handleForDevice}，deviceId 取 SIP From 头</li>
 *   <li>上级平台转发：{@link #handleForPlatform}，deviceId 取平台 serverGbId</li>
 * </ul>
 *
 * <h3>200 OK 答复</h3>
 * <p>由 {@code MessageRequestProcessor} 在分发前统一回，本类不再自行 {@code responseAck}，
 * 避免与处理器的 ackQuietly 重复发 200 导致设备误判。</p>
 */
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
        // 设备侧：SIP From 头才是设备 ID，报文里的 DeviceID 是通道
        alarm.setDeviceId(device.getDeviceId());
        publishEvent(alarm);
    }

    @Override
    public void handleForPlatform(RequestEvent event, GbParentPlatform platform, Element root) {
        DeviceAlarm alarm = parse(root);
        if (alarm == null) {
            return;
        }
        // 平台侧：deviceId 用平台 serverGbId 标识来源平台
        alarm.setDeviceId(platform.getServerGbId());
        publishEvent(alarm);
    }

    // ============================================================
    // 解析
    // ============================================================

    private DeviceAlarm parse(Element root) {
        DeviceAlarm alarm = new DeviceAlarm();

        // DeviceID 在国标报警中是通道 ID（设备报警的"谁"指通道而非设备）
        String channelId = XmlUtils.childText(root, "DeviceID");
        alarm.setChannelId(channelId);

        alarm.setAlarmPriority(XmlUtils.childText(root, "AlarmPriority"));

        String method = XmlUtils.childText(root, "AlarmMethod");
        alarm.setAlarmMethod(method);
        alarm.setAlarmMethodDesc(resolveMethodDesc(method));

        // AlarmTime 必填，缺则整条报警丢弃（无时间戳的告警没意义）
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

        // 经纬度：可选项，缺则留空
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

        // AlarmType 子码：仅 alarmMethod=2/5/6 时 Info/AlarmType 才有意义
        Element info = root.element("Info");
        if (info != null) {
            alarm.setAlarmType(XmlUtils.childText(info, "AlarmType"));
        }

        alarm.setReceiveTime(LocalDateTime.now());
        return alarm;
    }

    /**
     * 报警方式 code 可组合（如 "12" = 电话+设备），逐位翻译成中文描述。
     * 解析失败或空返回空串，不抛异常。
     */
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

    /**
     * 解析 GB/T 28181 ISO8601 时间。海康/大华偶尔带毫秒或时区后缀，先剥再解析。
     *
     * @return 解析失败返回 null
     */
    private LocalDateTime parseTime(String s) {
        // 标准格式：YYYY-MM-DDTHH:mm:ss
        try {
            return LocalDateTime.parse(s);
        } catch (Exception ignored) {
        }
        // 带毫秒：YYYY-MM-DDTHH:mm:ss.SSS
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

    // ============================================================
    // 事件发布
    // ============================================================

    private void publishEvent(DeviceAlarm alarm) {
        try {
            eventPublisher.publishEvent(new AlarmEvent(this, alarm));
        } catch (Exception e) {
            // 事件发布失败不影响 SIP 处理主链路——报警是 best-effort
            log.error("[报警通知] 发布 AlarmEvent 失败 channelId={} err={}",
                    alarm.getChannelId(), e.getMessage());
        }
    }
}
