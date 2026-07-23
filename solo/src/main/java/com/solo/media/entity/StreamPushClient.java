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

import java.time.LocalDateTime;

/**
 * 推流方鉴权实体
 * <p>一条 = 一个推流方。推流 URL 上必须带 {@code ?token=<token>}，ZLM on_publish 时反查。</p>
 */
@Data
@TableName("stream_push_client")
public class StreamPushClient {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 推流方标识，用户填写；用于 stream_push.client_id 溯源 */
    private String clientId;

    /** 鉴权令牌（SecureRandom 32 字节 Base64URL，256 位熵） */
    private String token;

    /** 0 禁用 1 启用 */
    private Integer enabled;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
