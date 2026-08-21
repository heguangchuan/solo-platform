package com.solo.gb28181.event.position;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 国标设备定位 DTO
 *
 * <p>承载从 SIP 定位通知报文（Notify/CmdType=MobilePosition）中解析出来的字段，
 * 跨 {@code MobilePositionNotifyMessageHandler} → {@link PositionEvent} →
 * {@link PositionEventListener} 三层传递。定位按当前 Sprint 5 决定
 * <b>不落库</b>，只在监听器里日志输出——所以这里没有对应的 MyBatis-Plus 实体映射，
 * 用普通 {@code @Data} 即可。</p>
 *
 * <p>字段语义：</p>
 * <ul>
 *   <li>{@code deviceId} — 设备国标 ID（与 Alarm 不同，MobilePosition 报文中 DeviceID 指设备本身而非通道）</li>
 *   <li>{@code time} — 设备侧上报的定位时间（GB/T 28181 ISO8601 格式）</li>
 *   <li>{@code longitude / latitude} — WGS84 经纬度（度，浮点）</li>
 *   <li>{@code speed} — 速度（厂商口径不一：km/h 或 m/s；国标未强制，按设备原值透传）</li>
 *   <li>{@code direction} — 方向角（0~359，正北为 0、顺时针）</li>
 *   <li>{@code altitude} — 海拔（米）</li>
 *   <li>{@code receiveTime} — 平台收到报文的时间（用于和设备 time 对比，判断网络延迟）</li>
 * </ul>
 */
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
