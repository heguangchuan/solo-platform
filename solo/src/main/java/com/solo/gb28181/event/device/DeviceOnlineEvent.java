package com.solo.gb28181.event.device;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * <p>由 REGISTER 流程在设备入库/重新注册成功后发布。订阅者可异步触发 DeviceInfo / Catalog 拉取等动作。</p>
 */
@Getter
public class DeviceOnlineEvent extends ApplicationEvent {

    /** 设备国标 ID */
    private final String deviceId;

    /** 是否首次注册（区分新接入 vs 注册续订） */
    private final boolean firstTime;

    public DeviceOnlineEvent(Object source, String deviceId, boolean firstTime) {
        super(source);
        this.deviceId = deviceId;
        this.firstTime = firstTime;
    }
}
