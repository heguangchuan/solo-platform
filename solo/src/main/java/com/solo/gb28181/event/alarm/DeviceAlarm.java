package com.solo.gb28181.event.alarm;

import lombok.Data;

import java.time.LocalDateTime;

// 报警 XML 的 DeviceID 实际是通道，设备 ID 要从 SIP From 另取。
// alarmMethod 可能是多位组合，receiveTime 则是平台收到报文的时间。
@Data
public class DeviceAlarm {

    private String deviceId;
    private String channelId;
    private String alarmPriority;
    private String alarmMethod;
    private String alarmMethodDesc;
    private LocalDateTime alarmTime;
    private String alarmDescription;
    private String alarmType;
    private Double longitude;
    private Double latitude;
    private LocalDateTime receiveTime;
}
