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
 * 云台巡航点（一条巡航路线包含多个巡航点，每个巡航点关联一个预置位）
 */
@Schema(description = "云台巡航点")
@Data
@TableName("gb_ptz_cruise_point")
public class GbPtzCruisePoint {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "巡航路线 ID（gb_ptz_cruise.id）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long cruiseId;

    @Schema(description = "预置位编号（设备侧，对应 gb_ptz_preset.preset_id）")
    private Integer presetId;

    @Schema(description = "冗余：预置位名称，便于列表展示")
    private String presetName;

    @Schema(description = "本点单独停留秒数，NULL 用路线默认")
    private Integer staySeconds;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
