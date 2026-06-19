package com.solo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 提示词模板实体类
 * 对应数据库表 ai_prompt_template，存储可复用的创作模板
 */
@Data
@TableName("ai_prompt_template")
public class AiPromptTemplate {

    /**
     * 模板ID，主键，自增
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 模板编码
     */
    private String templateCode;

    /**
     * 模板类型: CHAT/COPYWRITING/IMAGE/VIDEO/TRANSLATION
     */
    private String templateType;

    /**
     * 应用场景: 小红书/抖音/广告语/...
     */
    private String scene;

    /**
     * 模板内容（支持占位符）
     */
    private String content;

    /**
     * 变量定义JSON
     */
    private String variables;

    /**
     * 示例输出
     */
    private String example;

    /**
     * 状态: 0禁用 1启用
     */
    private Integer status;

    /**
     * 排序
     */
    private Integer sort;

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

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
