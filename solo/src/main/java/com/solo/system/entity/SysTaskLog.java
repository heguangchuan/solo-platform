package com.solo.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Schema(description = "定时任务日志")
@Data
@TableName("sys_task_log")
public class SysTaskLog {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "任务 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "任务编码")
    private String taskCode;

    @Schema(description = "handler bean 名")
    private String handlerName;

    @Schema(description = "任务参数（JSON 字符串）")
    private String taskParams;

    @Schema(description = "执行时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime executionTime;

    @Schema(description = "耗时（毫秒）")
    private Long durationMs;

    @Schema(description = "结果状态（0=失败，1=成功）")
    private Integer resultStatus;

    @Schema(description = "错误信息")
    private String errorMsg;
}
