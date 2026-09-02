package com.solo.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * AI会话实体类
 * 对应数据库表 ai_conversation，存储用户与Agnes的对话会话
 */
@Schema(description = "AI 会话")
@Data
@TableName("ai_conversation")
public class AiConversation {

    /**
     * 会话ID
     */
    @Schema(description = "会话 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 用户ID
     */
    @Schema(description = "用户 ID")
    private Long userId;

    /**
     * 会话标题
     */
    @Schema(description = "会话标题")
    private String title;

    /**
     * 会话类型: CHAT对话/CREATION创作/TASK任务
     */
    @Schema(description = "会话类型（CHAT 对话/CREATION 创作/TASK 任务）")
    private String conversationType;

    /**
     * 使用的模型配置ID
     */
    @Schema(description = "使用的模型配置 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelConfigId;

    /**
     * 最后一条消息时间
     */
    @Schema(description = "最后一条消息时间")
    private LocalDateTime lastMessageTime;

    /**
     * 消息总数
     */
    @Schema(description = "消息数")
    private Integer messageCount;

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
