package com.solo.media.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 推流方鉴权实体
 * <p>一条 = 一个推流方。推流 URL 上必须带 {@code ?token=<token>}，ZLM on_publish 时反查。</p>
 */
@Schema(description = "推流方鉴权")
@Data
@TableName("stream_push_client")
public class StreamPushClient {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 推流方标识，用户填写；用于 stream_push.client_id 溯源 */
    @Schema(description = "推流方标识，用户填写；用于 stream_push.client_id 溯源")
    private String clientId;

    /** 鉴权令牌（SecureRandom 32 字节 Base64URL，256 位熵） */
    @Schema(description = "鉴权令牌（SecureRandom 32 字节 Base64URL，256 位熵）")
    private String token;

    /** 0 禁用 1 启用 */
    @Schema(description = "启用（0/1）")
    private Integer enabled;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
