package com.solo.gb28181.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Schema(description = "设备报警")
@Data
@TableName("gb_device_alarm")
public class GbDeviceAlarm {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "报警所属设备国标 ID")
    private String deviceId;

    @Schema(description = "报警通道国标 ID")
    private String channelId;

    @Schema(description = "通道名称")
    private String channelName;

    @Schema(description = "报警级别 1~4（1 最严重）")
    private Integer alarmPriority;

    @Schema(description = "报警方式原始码")
    private String alarmMethod;

    @Schema(description = "报警方式描述")
    private String alarmMethodDesc;

    @Schema(description = "报警类型子码")
    private String alarmType;

    @Schema(description = "设备上报的报警发生时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime alarmTime;

    @Schema(description = "报警内容描述")
    private String alarmDescription;

    @Schema(description = "经度")
    private Double longitude;

    @Schema(description = "纬度")
    private Double latitude;

    @Schema(description = "处置状态：0 未处理、1 已确认、2 已处置、3 误报")
    private Integer handleStatus;

    @Schema(description = "处置备注")
    private String handleRemark;

    @Schema(description = "最后处置人")
    private String handleUser;

    @Schema(description = "最后处置时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handleTime;

    @Schema(description = "平台收到报警时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime receiveTime;

    @Schema(description = "入库时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
