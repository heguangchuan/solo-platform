package com.solo.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI创作任务实体类
 * 对应数据库表 ai_generation_task，统一记录文本、图片、视频生成任务
 */
@Schema(description = "AI 创作任务")
@Data
@TableName("ai_generation_task")
public class AiGenerationTask {

    /**
     * 任务ID
     */
    @Schema(description = "任务 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 用户ID
     */
    @Schema(description = "用户 ID")
    private Long userId;

    /**
     * 关联会话ID
     */
    @Schema(description = "关联会话 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    /**
     * 关联消息ID
     */
    @Schema(description = "关联消息 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long messageId;

    /**
     * 任务类型: TEXT/IMAGE/VIDEO/AUDIO
     */
    @Schema(description = "任务类型（TEXT/IMAGE/VIDEO/AUDIO）")
    private String taskType;

    /**
     * 用户原始输入
     */
    @Schema(description = "用户原始输入")
    private String prompt;

    /**
     * 优化后的提示词
     */
    @Schema(description = "优化后的提示词")
    private String enhancedPrompt;

    /**
     * 使用的模板ID
     */
    @Schema(description = "使用的模板 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    /**
     * 使用的模型配置ID
     */
    @Schema(description = "使用的模型配置 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelConfigId;

    /**
     * 使用的模型名称
     */
    @Schema(description = "使用的模型名称")
    private String modelName;

    /**
     * 模型参数JSON
     */
    @Schema(description = "模型参数（JSON 字符串）")
    private String modelParams;

    /**
     * 任务状态: PENDING待处理/PROCESSING处理中/SUCCESS成功/FAILED失败/CANCELLED已取消
     */
    @Schema(description = "任务状态（PENDING 待处理/PROCESSING 处理中/SUCCESS 成功/FAILED 失败/CANCELLED 已取消）")
    private String status;

    /**
     * 结果类型: TEXT/URL/FILE
     */
    @Schema(description = "结果类型（TEXT/URL/FILE）")
    private String resultType;

    /**
     * 结果内容（文本或URL）
     */
    @Schema(description = "结果内容（文本或 URL）")
    private String resultContent;

    /**
     * 错误信息
     */
    @Schema(description = "错误信息")
    private String errorMsg;

    /**
     * 消耗Token数
     */
    @Schema(description = "消耗 Token 数")
    private Integer tokens;

    /**
     * 估算成本
     */
    @Schema(description = "估算成本")
    private BigDecimal cost;

    /**
     * 开始执行时间
     */
    @Schema(description = "开始执行时间")
    private LocalDateTime startTime;

    /**
     * 完成时间
     */
    @Schema(description = "完成时间")
    private LocalDateTime finishTime;

    /**
     * 逻辑删除标识: 0未删除 1已删除
     */
    @Schema(description = "逻辑删除（0=未删，1=已删）")
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
