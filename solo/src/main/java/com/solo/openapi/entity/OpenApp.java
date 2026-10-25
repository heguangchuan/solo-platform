package com.solo.openapi.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "开放API应用")
@Data
@TableName("open_app")
public class OpenApp implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "应用标识（随请求传输）")
    private String appKey;

    @Schema(description = "应用名称")
    private String appName;

    @Schema(description = "联系人")
    private String contact;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "当前密钥密文")
    @TableField(select = false)
    private String secretCipher;

    @Schema(description = "轮换期旧密钥密文")
    @TableField(select = false)
    private String oldSecretCipher;

    @Schema(description = "旧密钥失效时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime oldSecretExpireTime;

    @Schema(description = "授权范围（逗号分隔）")
    private String scopes;

    @Schema(description = "IP白名单（逗号分隔）")
    private String ipWhitelist;

    @Schema(description = "Webhook 回调地址")
    private String callbackUrl;

    @Schema(description = "回调签名密钥密文")
    @TableField(select = false)
    private String callbackSecretCipher;

    /** 是否已配置回调签名密钥，非数据库字段，出参时根据密文现算，密文本身不下发 */
    @Schema(description = "是否已配置回调签名密钥")
    @TableField(exist = false)
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Boolean callbackSecretSet;

    @Schema(description = "订阅事件类型（逗号分隔）")
    private String eventSubscriptions;

    @Schema(description = "QPS上限，0=不限")
    private Integer qpsLimit;

    @Schema(description = "日调用量上限，0=不限")
    private Long dailyLimit;

    @Schema(description = "状态 0停用 1启用")
    private Integer status;

    @Schema(description = "过期时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    @Schema(description = "备注")
    private String remark;

    @TableLogic
    private Integer deleted;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
