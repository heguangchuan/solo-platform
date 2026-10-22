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
import java.util.List;

@Schema(description = "云端录像计划（周计划）")
@Data
@TableName("gb_record_plan")
public class GbRecordPlan {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "计划名称")
    private String name;

    @Schema(description = "设备国标 ID")
    private String deviceId;

    @Schema(description = "通道国标 ID")
    private String channelId;

    @Schema(description = "是否启用 0停 1启")
    private Integer enabled;

    @Schema(description = "传输模式 UDP/TCP-active/TCP-passive，空随设备")
    private String transport;

    @Schema(description = "码流 token（空=主码流）")
    private String streamNumber;

    @Schema(description = "本计划录像保留天数，0=跟随全局")
    private Integer retentionDays;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // 周时段列表，只在详情/保存请求里用，不映射列
    @TableField(exist = false)
    private List<GbRecordPlanPeriod> periods;
}
