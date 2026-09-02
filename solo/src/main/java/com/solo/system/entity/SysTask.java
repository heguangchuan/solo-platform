package com.solo.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Schema(description = "定时任务")
@Data
@TableName("sys_task")
public class SysTask {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "任务编码")
    private String taskCode;

    @Schema(description = "cron 表达式")
    private String cron;

    @Schema(description = "handler bean 名")
    private String handlerName;

    @Schema(description = "任务参数（JSON 字符串）")
    private String taskParams;

    @Schema(description = "状态（0=停用，1=启用）")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "逻辑删除（0=未删，1=已删）")
    @TableLogic
    private Integer deleted;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
