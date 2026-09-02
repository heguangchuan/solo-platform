package com.solo.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * AI供应商异步任务实体。
 */
@Schema(description = "AI 供应商异步任务")
@Data
@TableName("ai_provider_task")
public class AiProviderTask {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "generationTaskId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long generationTaskId;

    @Schema(description = "会话 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "任务类型（TEXT/IMAGE/VIDEO/AUDIO）")
    private String taskType;

    @Schema(description = "厂商（openai/deepseek/kimi/anthropic 等）")
    private String provider;

    @Schema(description = "模型名")
    private String modelName;

    @Schema(description = "modelConfigId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelConfigId;

    @Schema(description = "providerTaskId")
    private String providerTaskId;

    @Schema(description = "providerVideoId")
    private String providerVideoId;

    @Schema(description = "providerStatus")
    private String providerStatus;

    @Schema(description = "进度（0-100）")
    private Integer progress;

    @Schema(description = "结果 URL")
    private String resultUrl;

    @Schema(description = "错误信息")
    private String errorMsg;

    @Schema(description = "seconds")
    private String seconds;

    @Schema(description = "size")
    private String size;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "完成时间")
    private LocalDateTime finishTime;

    @Schema(description = "下次轮询时间")
    private LocalDateTime nextCheckTime;

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
