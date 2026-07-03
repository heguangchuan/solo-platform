package com.solo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * AI素材实体类
 * 对应数据库表 ai_asset，保存生成后的图片、视频、音频、文档
 */
@Data
@TableName("ai_asset")
public class AiAsset {

    /**
     * 素材ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 素材类型: IMAGE/VIDEO/AUDIO/DOCUMENT
     */
    private String assetType;

    /**
     * 来源: SYSTEM_UPLOAD系统上传 MODEL_GENERATE模型生成
     */
    private String source;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件访问URL
     */
    private String fileUrl;

    /**
     * 封面图URL
     */
    private String coverUrl;

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
}
