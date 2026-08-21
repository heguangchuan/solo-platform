package com.solo.gb28181.event.device;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 设备离线事件
 * <p>由 REGISTER 注销 / 心跳超时 / 手动下线发布。</p>
 */
@Getter
public class DeviceOfflineEvent extends ApplicationEvent {

    private final String deviceId;
    private final String reason;

    public DeviceOfflineEvent(Object source, String deviceId, String reason) {
        super(source);
        this.deviceId = deviceId;
        this.reason = reason;
    }
}
