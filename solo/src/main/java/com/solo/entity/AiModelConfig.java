package com.solo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI模型配置实体类
 * 对应数据库表 ai_model_config，存储AI模型的配置信息
 */
@Data
@TableName("ai_model_config")
public class AiModelConfig {

    /**
     * 配置ID，主键，自增
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 模型来源
     */
    private String provider;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 模型类型: TEXT/IMAGE/VIDEO/EMBEDDING/AUDIO
     */
    private String modelType;

    /**
     * API密钥
     */
    private String apiKey;

    /**
     * API基础地址
     */
    private String baseUrl;

    /**
     * 模型参数配置JSON
     */
    private String modelParams;

    /**
     * 是否支持图片输入: 0否 1是
     */
    private Integer supportVision;

    /**
     * 状态: 0禁用 1启用
     */
    private Integer status;

    /**
     * 排序
     */
    private Integer sort;

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
