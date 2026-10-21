package com.solo.gb28181.event.alarm;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * <p>承载从 SIP 报警通知报文中解析出来的字段，跨 {@code AlarmNotifyMessageHandler} →
 * {@code AlarmEvent} → {@code AlarmEventListener} 三层传递。报警按当前 Sprint 决定
 * <b>不落库</b>，只在监听器里日志输出——所以这里没有对应的 MyBatis-Plus 实体映射，
 * 用普通 {@code @Data} 即可。</p>
 *
 * <p>字段语义：</p>
 * <ul>
 *   <li>{@code deviceId} — 报警所属设备的国标 ID；来自 SIP From 头（设备侧）或上级 serverGbId（平台侧）</li>
 *   <li>{@code channelId} — 报警通道国标 ID；来自报文 {@code <DeviceID>}（国标规定设备报警中 DeviceID 是通道）</li>
 *   <li>{@code alarmPriority} — 报警级别 1~4（一级最严重）</li>
 *   <li>{@code alarmMethod} — 报文原始 method code（"1" ~ "7654321" 任意组合）</li>
 *   <li>{@code alarmMethodDesc} — 把 code 拆位翻译成"电话报警 / 视频报警 / ..."的友好串</li>
 *   <li>{@code alarmTime} — 设备侧上报的报警发生时间（GB/T 28181 ISO8601 格式）</li>
 *   <li>{@code alarmDescription} — 报警内容描述（设备给的自由文本）</li>
 *   <li>{@code alarmType} — 报警类型子码；alarmMethod=2 设备报警 / 5 视频报警 / 6 设备故障时才有意义</li>
 *   <li>{@code longitude / latitude} — 报警发生位置（GPS / 移动设备才有）</li>
 *   <li>{@code receiveTime} — 平台收到报文的时间（用于和设备 alarmTime 对比，判断网络延迟）</li>
 * </ul>
 */
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
