package com.solo.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI创作任务实体类
 * 对应数据库表 ai_generation_task，统一记录文本、图片、视频生成任务
 */
@Data
@TableName("ai_generation_task")
public class AiGenerationTask {

    /**
     * 任务ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 关联会话ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    /**
     * 关联消息ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long messageId;

    /**
     * 任务类型: TEXT/IMAGE/VIDEO/AUDIO
     */
    private String taskType;

    /**
     * 用户原始输入
     */
    private String prompt;

    /**
     * 优化后的提示词
     */
    private String enhancedPrompt;

    /**
     * 使用的模板ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    /**
     * 使用的模型配置ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelConfigId;

    /**
     * 使用的模型名称
     */
    private String modelName;

    /**
     * 模型参数JSON
     */
    private String modelParams;

    /**
     * 任务状态: PENDING待处理/PROCESSING处理中/SUCCESS成功/FAILED失败/CANCELLED已取消
     */
    private String status;

    /**
     * 结果类型: TEXT/URL/FILE
     */
    private String resultType;

    /**
     * 结果内容（文本或URL）
     */
    private String resultContent;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 消耗Token数
     */
    private Integer tokens;

    /**
     * 估算成本
     */
    private BigDecimal cost;

    /**
     * 开始执行时间
     */
    private LocalDateTime startTime;

    /**
     * 完成时间
     */
    private LocalDateTime finishTime;

    /**
     * 逻辑删除标识: 0未删除 1已删除
     */
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
