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

/**
 * <h3>报文结构</h3>
 * <pre>
 * &lt;Notify&gt;
 *   &lt;CmdType&gt;MobilePosition&lt;/CmdType&gt;
 *   &lt;SN&gt;...&lt;/SN&gt;
 *   &lt;DeviceID&gt;设备国标ID&lt;/DeviceID&gt;     &lt;!-- 注意：与 Alarm 不同，DeviceID 是设备本身 --&gt;
 *   &lt;Time&gt;2024-01-15T14:30:00&lt;/Time&gt;
 *   &lt;Longitude&gt;116.397128&lt;/Longitude&gt;
 *   &lt;Latitude&gt;39.916527&lt;/Latitude&gt;
 *   &lt;Speed&gt;0.0&lt;/Speed&gt;                 &lt;!-- 可选，厂商口径 km/h 或 m/s 不一致，原值透传 --&gt;
 *   &lt;Direction&gt;0.00&lt;/Direction&gt;          &lt;!-- 可选，0~359，正北为 0 --&gt;
 *   &lt;Altitude&gt;0.00&lt;/Altitude&gt;           &lt;!-- 可选 --&gt;
 * &lt;/Notify&gt;
 * </pre>
 *
 * <h3>职责</h3>
 * <ol>
 *   <li>解析 XML 字段填进 {@link DevicePosition} DTO</li>
 *   <li>发布 {@link PositionEvent}，由日志监听器 {@link com.solo.gb28181.event.position.PositionEventListener}
 *       和落库监听器 {@link com.solo.gb28181.event.position.PositionPersistenceListener} 并行异步处理</li>
 *   <li>定位为高频上报，不推 WebSocket；轨迹点写入 gb_mobile_position 并按保留期定期清理</li>
 * </ol>
 *
 * <h3>来源分支</h3>
 * <ul>
 *   <li>下级设备上报：{@link #handleForDevice}，deviceId 取 SIP From 头（与报文 DeviceID 一致，互为冗余校验）</li>
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
        // 设备侧：SIP From 头是设备 ID，与报文 DeviceID 应一致；以 SIP 头为权威
        position.setDeviceId(device.getDeviceId());
        publishEvent(position);
    }

    @Override
    public void handleForPlatform(RequestEvent event, GbParentPlatform platform, Element root) {
        DevicePosition position = parse(root);
        if (position == null) {
            return;
        }
        // 平台侧：deviceId 用平台 serverGbId 标识来源平台
        position.setDeviceId(platform.getServerGbId());
        publishEvent(position);
    }

    // ============================================================
    // 解析
    // ============================================================

    private DevicePosition parse(Element root) {
        DevicePosition position = new DevicePosition();

        // DeviceID 在国标定位中是设备 ID（与 Alarm 不同，Alarm 的 DeviceID 是通道）
        position.setDeviceId(XmlUtils.childText(root, "DeviceID"));

        // Time 必填，缺则整条定位丢弃（无时间戳的轨迹点没意义）
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

        // 经纬度：可选项，缺则留空（GPS 信号丢失或室内设备无定位属正常）
        position.setLongitude(parseDouble(XmlUtils.childText(root, "Longitude")));
        position.setLatitude(parseDouble(XmlUtils.childText(root, "Latitude")));

        // 速度 / 方向 / 海拔：可选项
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

    private void publishEvent(DevicePosition position) {
        try {
            eventPublisher.publishEvent(new PositionEvent(this, position));
        } catch (Exception e) {
            // 事件发布失败不影响 SIP 处理主链路——定位是 best-effort
            log.error("[移动位置] 发布 PositionEvent 失败 deviceId={} err={}",
                    position.getDeviceId(), e.getMessage());
        }
    }
}
