package com.solo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * AI供应商异步任务实体。
 */
@Data
@TableName("ai_provider_task")
public class AiProviderTask {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long generationTaskId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    private Long userId;
    private String username;
    private String taskType;
    private String provider;
    private String modelName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelConfigId;

    private String providerTaskId;
    private String providerVideoId;
    private String providerStatus;
    private Integer progress;
    private String resultUrl;
    private String errorMsg;
    private String seconds;
    private String size;

    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    private LocalDateTime nextCheckTime;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
