package com.solo.gb28181.event.alarm;

import lombok.Getter;

// AlarmMethod 可能是 "12" 这种组合，得逐位拆，不能只按一个枚举值查。
@Getter
public enum DeviceAlarmMethod {

    Telephone(1, "电话报警"),
    Device(2, "设备报警"),
    SMS(3, "短信报警"),
    GPS(4, "GPS 报警"),
    Video(5, "视频报警"),
    DeviceFailure(6, "设备故障报警"),
    Other(7, "其他报警");

    private final int val;
    private final String desc;

    DeviceAlarmMethod(int val, String desc) {
        this.val = val;
        this.desc = desc;
    }

    public static DeviceAlarmMethod typeOf(int code) {
        for (DeviceAlarmMethod m : values()) {
            if (m.val == code) {
                return m;
            }
        }
        return null;
    }
}
