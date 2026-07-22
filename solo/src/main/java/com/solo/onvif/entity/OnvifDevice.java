package com.solo.onvif.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * ONVIF 设备实体
 * <p>对应数据库表 onvif_device。身份靠 host:port + 账号密码，与国标的 20 位 device_id + 平台口令 Digest 完全不同，
 * 因此独立于 gb_device 建表（见实施计划 Context）。</p>
 */
@Data
@TableName("onvif_device")
public class OnvifDevice {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 显示名（用户填） */
    private String name;

    /** 设备 IP */
    private String host;

    /** ONVIF Web 服务端口，默认 80 */
    private Integer port;

    /** 鉴权用户名 */
    private String username;

    /** 鉴权密码（明文存储，与现有代码风格一致） */
    private String password;

    /** RTSP 拉流传输协议：TCP / UDP，默认 TCP */
    private String transport;

    /** 码流类型：0 主码流 1 子码流 */
    private Integer streamType;

    /** 厂商（GetDeviceInformation 探测填充） */
    private String manufacturer;

    /** 型号 */
    private String model;

    /** 固件版本 */
    private String firmware;

    /** 设备序列号 */
    private String serialNumber;

    /** 当前选定的 profile token（GetProfiles 探测填充） */
    private String profileToken;

    /** GetStreamUri 探测填充的原始 RTSP（不含账密） */
    private String streamUri;

    /** 在线状态：0 离线 1 在线（由探测任务改） */
    private Integer status;

    /** 最近一次探测时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastCheckTime;

    /** 探测失败原因 */
    private String errorMsg;

    /** 数据来源：1 系统新增 2 表格导入 */
    private Integer source;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
