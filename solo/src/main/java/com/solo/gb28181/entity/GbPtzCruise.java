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

/**
 * 云台巡航路线
 */
@Schema(description = "云台巡航路线")
@Data
@TableName("gb_ptz_cruise")
public class GbPtzCruise {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "通道数据库主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long channelDbId;

    @Schema(description = "巡航路线编号（设备侧，1~N）")
    private Integer cruiseId;

    @Schema(description = "巡航路线名称")
    private String name;

    @Schema(description = "巡航速度（1~255）")
    private Integer speed;

    @Schema(description = "每个预置位默认停留秒数")
    private Integer staySeconds;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
