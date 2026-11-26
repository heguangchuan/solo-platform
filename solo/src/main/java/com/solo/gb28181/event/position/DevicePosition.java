package com.solo.gb28181.event.position;

import lombok.Data;

import java.time.LocalDateTime;

// MobilePosition 的 DeviceID 是设备，不是报警那种通道。
// speed 有厂商按 km/h，也有按 m/s，国标没统一，这里只透传。
@Data
public class DevicePosition {

    private String deviceId;
    private LocalDateTime time;
    private Double longitude;
    private Double latitude;
    private Double speed;
    private Double direction;
    private Double altitude;
    private LocalDateTime receiveTime;
}
