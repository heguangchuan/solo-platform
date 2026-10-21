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

@Schema(description = "设备移动位置")
@Data
@TableName("gb_mobile_position")
public class GbMobilePosition {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "设备国标 ID")
    private String deviceId;

    @Schema(description = "设备名称（落库时冗余）")
    private String deviceName;

    @Schema(description = "设备上报的定位时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime positionTime;

    @Schema(description = "经度（WGS84）")
    private Double longitude;

    @Schema(description = "纬度（WGS84）")
    private Double latitude;

    @Schema(description = "海拔（米）")
    private Double altitude;

    @Schema(description = "速度（设备原值，km/h 或 m/s 视厂商而定）")
    private Double speed;

    @Schema(description = "方向角 0~359，正北为 0 顺时针")
    private Double direction;

    @Schema(description = "平台收到报文时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime receiveTime;

    @Schema(description = "入库时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
