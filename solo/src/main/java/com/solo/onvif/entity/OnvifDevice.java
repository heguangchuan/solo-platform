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
 *
 * <h4>不持久化的探测字段</h4>
 * <p>{@code GetDeviceInformation} 拿到的厂商/型号/固件/序列号只打日志，不落库；
 * {@code GetProfiles + GetStreamUri} 拿到的 RTSP 地址（{@link #streamUri}）和在线状态（{@link #status}）持久化，
 * 供点播时直接使用。</p>
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

    /** 在线状态：0 离线 1 在线（由探测任务改） */
    private Integer status;

    /** GetStreamUri 探测填充的原始 RTSP（不含账密） */
    private String streamUri;

    /** 最近一次探测时间（日志也打，前端列表展示用） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastCheckTime;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
