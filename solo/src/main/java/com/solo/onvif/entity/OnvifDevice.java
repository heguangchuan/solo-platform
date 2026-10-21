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
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "ONVIF 设备")
@Data
@TableName("onvif_device")
public class OnvifDevice {

    @Schema(description = "ONVIF 设备主键")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "设备名（显示名，用户填写）")
    private String name;

    @Schema(description = "IP 地址（设备 IP）")
    private String host;

    @Schema(description = "ONVIF Web 服务端口，默认 80")
    private Integer port;

    @Schema(description = "鉴权用户名")
    private String username;

    @Schema(description = "鉴权密码（明文存储，与现有代码风格一致）")
    private String password;

    @Schema(description = "在线状态：0 离线，1 在线（由探测任务更新）")
    private Integer status;

    @Schema(description = "GetStreamUri 探测填充的原始 RTSP 流地址（不含账密）")
    private String streamUri;

    @Schema(description = "最近一次探测时间（日志也打，前端列表展示用）")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastCheckTime;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
