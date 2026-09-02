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
 * AI消息实体类
 * 对应数据库表 ai_message，存储每轮对话的消息内容
 */
@Schema(description = "AI 消息")
@Data
@TableName("ai_message")
public class AiMessage {

    /**
     * 消息ID
     */
    @Schema(description = "消息 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 会话ID
     */
    @Schema(description = "会话 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    /**
     * 角色: USER用户/ASSISTANT助手/SYSTEM系统/TOOL工具
     */
    @Schema(description = "角色（USER 用户/ASSISTANT 助手/SYSTEM 系统/TOOL 工具）")
    private String role;

    /**
     * 消息内容
     */
    @Schema(description = "消息内容")
    private String content;

    /**
     * 消息类型: TEXT/IMAGE/VIDEO/AUDIO
     */
    @Schema(description = "消息类型（TEXT/IMAGE/VIDEO/AUDIO）")
    private String messageType;

    /**
     * 使用的模型名称
     */
    @Schema(description = "使用的模型名称")
    private String modelName;

    /**
     * 消耗Token总数
     */
    @Schema(description = "消耗 Token 总数")
    private Integer tokens;

    /**
     * 输入Token数
     */
    @Schema(description = "输入 Token 数")
    private Integer promptTokens;

    /**
     * 输出Token数
     */
    @Schema(description = "输出 Token 数")
    private Integer completionTokens;

    /**
     * 估算成本
     */
    @Schema(description = "估算成本")
    private BigDecimal cost;

    /**
     * 错误信息
     */
    @Schema(description = "错误信息")
    private String errorMsg;

    /**
     * 附件列表 JSON: [{"type":"IMAGE","url":"...","mimeType":"image/png","fileName":"...","assetId":"..."}]
     */
    @Schema(description = "附件列表 JSON")
    private String attachments;

    /**
     * 元数据JSON
     */
    @Schema(description = "元数据 JSON")
    private String metadata;

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
}
