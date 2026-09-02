package com.solo.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * AI素材实体类
 * 对应数据库表 ai_asset，保存生成后的图片、视频、音频、文档
 */
@Schema(description = "AI 素材")
@Data
@TableName("ai_asset")
public class AiAsset {

    /**
     * 素材ID
     */
    @Schema(description = "素材 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 素材类型: IMAGE/VIDEO/AUDIO/DOCUMENT
     */
    @Schema(description = "素材类型（IMAGE/VIDEO/AUDIO/DOCUMENT）")
    private String assetType;

    /**
     * 来源: SYSTEM_UPLOAD系统上传 MODEL_GENERATE模型生成
     */
    @Schema(description = "来源（SYSTEM_UPLOAD 系统上传/MODEL_GENERATE 模型生成）")
    private String source;

    /**
     * 文件名
     */
    @Schema(description = "文件名")
    private String fileName;

    /**
     * 文件访问URL
     */
    @Schema(description = "文件 URL")
    private String fileUrl;

    /**
     * 封面图URL
     */
    @Schema(description = "封面 URL")
    private String coverUrl;

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
