package com.solo.gb28181.event.alarm;

import lombok.Getter;

/**
 * 国标报警方式枚举
 *
 * <p>对应 GB/T 28181-2016 报警通知 (Notify/CmdType=Alarm) 中的 {@code <AlarmMethod>} 字段。
 * 国标允许把多个方式组合上报（"12" 表示电话+设备），所以实际字段是字符串，
 * 解析时按位拆，每个字符映射到本枚举的一项。</p>
 *
 * <p>数值约定：</p>
 * <ul>
 *   <li>1 - 电话报警</li>
 *   <li>2 - 设备报警</li>
 *   <li>3 - 短信报警</li>
 *   <li>4 - GPS 报警</li>
 *   <li>5 - 视频报警</li>
 *   <li>6 - 设备故障报警</li>
 *   <li>7 - 其他报警</li>
 * </ul>
 */
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

    /**
     * 按数值查枚举；未匹配返回 null
     */
    public static DeviceAlarmMethod typeOf(int code) {
        for (DeviceAlarmMethod m : values()) {
            if (m.val == code) {
                return m;
            }
        }
        return null;
    }
}
