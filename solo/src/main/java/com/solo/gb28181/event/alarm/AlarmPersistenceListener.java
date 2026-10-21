package com.solo.gb28181.event.alarm;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.solo.gb28181.entity.GbDeviceAlarm;
import com.solo.gb28181.service.GbDeviceAlarmService;
import com.solo.websocket.SystemMessageWebSocket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>与只打日志的 {@link AlarmEventListener} 并行订阅 {@link AlarmEvent}，互不影响：
 * 本监听器负责落库（报警中心数据源）并通过 WebSocket 广播给所有在线用户。
 * 异步执行，不阻塞 SIP 报文处理线程。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmPersistenceListener {

    private final GbDeviceAlarmService alarmService;
    private final ObjectMapper objectMapper;

    @Async("gb28181BizExecutor")
    @EventListener
    public void onAlarm(AlarmEvent event) {
        DeviceAlarm source = event.getAlarm();
        try {
            GbDeviceAlarm saved = alarmService.saveFromEvent(source);
            pushWebSocket(saved);
        } catch (Exception e) {
            // 落库/推送失败不能影响 SIP 主链路与其它监听器
            log.error("[报警中心] 报警落库失败 deviceId={} channelId={}",
                    source.getDeviceId(), source.getChannelId(), e);
        }
    }

    /**
     * 广播报警：消息类型 DEVICE_ALARM，前端据此弹通知并可跳转报警中心
     */
    private void pushWebSocket(GbDeviceAlarm alarm) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "DEVICE_ALARM");
        payload.put("data", alarm);
        payload.put("title", "设备报警");
        payload.put("content", buildContent(alarm));
        payload.put("route", "/gb28181/alarm");
        payload.put("createTime", alarm.getCreateTime());
        try {
            int count = SystemMessageWebSocket.broadcast(objectMapper.writeValueAsString(payload));
            log.info("[报警中心] 报警已广播 id={} 推送人数={}", alarm.getId(), count);
        } catch (JacksonException e) {
            log.warn("[报警中心] 报警消息序列化失败 id={}", alarm.getId(), e);
        }
    }

    private String buildContent(GbDeviceAlarm alarm) {
        String name = alarm.getChannelName() != null ? alarm.getChannelName() : alarm.getChannelId();
        String desc = alarm.getAlarmDescription() != null ? alarm.getAlarmDescription()
                : alarm.getAlarmMethodDesc();
        return (name == null ? alarm.getDeviceId() : name) + (desc != null ? "：" + desc : "");
    }
}
