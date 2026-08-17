package com.solo.gb28181.event.alarm;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 设备报警事件
 *
 * <p>由 {@code AlarmNotifyMessageHandler} 在解析完 SIP 报警通知报文后发布。
 * 当前订阅者 {@link AlarmEventListener} 仅日志输出；后续要落库 / 推 WebSocket / 发企微
 * 都可以挂在这里，不影响 handler 主链路。</p>
 */
@Getter
public class AlarmEvent extends ApplicationEvent {

    private final DeviceAlarm alarm;

    public AlarmEvent(Object source, DeviceAlarm alarm) {
        super(source);
        this.alarm = alarm;
    }
}
