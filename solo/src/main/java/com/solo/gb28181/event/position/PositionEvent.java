package com.solo.gb28181.event.position;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * <p>由 {@code MobilePositionNotifyMessageHandler} 在解析完 SIP 定位通知报文后发布。
 * 当前订阅者 {@link PositionEventListener} 仅日志输出；后续要落库 / 推 WebSocket /
 * 写时序数据库都可以挂在这里，不影响 handler 主链路。</p>
 */
@Getter
public class PositionEvent extends ApplicationEvent {

    private final DevicePosition position;

    public PositionEvent(Object source, DevicePosition position) {
        super(source);
        this.position = position;
    }
}
