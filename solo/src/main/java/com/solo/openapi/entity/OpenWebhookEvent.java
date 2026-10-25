package com.solo.openapi.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "开放API Webhook事件")
@Data
@TableName("open_webhook_event")
public class OpenWebhookEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    // 0待投递 1成功 2待重试 3放弃
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_RETRY = 2;
    public static final int STATUS_GIVE_UP = 3;

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "事件唯一ID")
    private String eventId;

    @Schema(description = "目标应用ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    @Schema(description = "目标应用标识")
    private String appKey;

    @Schema(description = "事件类型")
    private String eventType;

    @Schema(description = "事件报文JSON")
    private String payload;

    @Schema(description = "投递回调地址快照")
    private String callbackUrl;

    @Schema(description = "状态 0待投递 1成功 2待重试 3放弃")
    private Integer status;

    @Schema(description = "已重试次数")
    private Integer retryCount;

    @Schema(description = "最大重试次数")
    private Integer maxRetry;

    @Schema(description = "下次投递时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nextRetryTime;

    @Schema(description = "最近失败原因")
    private String lastError;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
