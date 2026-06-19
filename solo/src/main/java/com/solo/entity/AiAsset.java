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
     * 用户ID
     */
    private Long userId;

    /**
     * 关联任务ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /**
     * 素材类型: IMAGE/VIDEO/AUDIO/DOCUMENT
     */
    private String assetType;

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
     * 缩略图URL
     */
    private String thumbnailUrl;

    /**
     * MIME类型
     */
    private String mimeType;

    /**
     * 文件扩展名
     */
    private String ext;

    /**
     * 文件大小(字节)
     */
    private Long size;

    /**
     * 时长(秒，音视频)
     */
    private Integer duration;

    /**
     * 宽度(像素，图片视频)
     */
    private Integer width;

    /**
     * 高度(像素，图片视频)
     */
    private Integer height;

    /**
     * 生成指令
     */
    private String prompt;

    /**
     * 生成模型
     */
    private String modelName;

    /**
     * 状态: 0无效 1有效
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

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
