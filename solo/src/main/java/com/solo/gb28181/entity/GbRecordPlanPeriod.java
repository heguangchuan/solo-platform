package com.solo.gb28181.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Schema(description = "录像计划周时段")
@Data
@TableName("gb_record_plan_period")
public class GbRecordPlanPeriod {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属录像计划 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long planId;

    @Schema(description = "星期 1周一 ... 7周日")
    private Integer dayOfWeek;

    @Schema(description = "开始时间 HH:mm（支持跨午夜）")
    private String startTime;

    @Schema(description = "结束时间 HH:mm，跨午夜时早于开始时间")
    private String endTime;
}
