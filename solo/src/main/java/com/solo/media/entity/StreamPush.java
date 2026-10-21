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

@Schema(description = "推流")
@Data
@TableName("stream_push")
public class StreamPush {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "ZLM app 名")
    private String app;

    @Schema(description = "ZLM 流名")
    private String stream;

    @Schema(description = "承载流节点 serverId")
    private String mediaServerId;

    @Schema(description = "ZLM vhost（默认 __defaultVhost__）")
    private String vhost;

    @Schema(description = "推流协议（rtsp/rtmp/hls/ts/fmp4，通常只留 rtsp）")
    private String streamSchema;

    @Schema(description = "推流类型：rtmp / rtsp / rtc（ZLM originType 整数已映射为字符串再入库）")
    private String originType;

    @Schema(description = "推流存活秒数")
    private Integer aliveSecond;

    @Schema(description = "首次开始推流时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime pushTime;

    @Schema(description = "推流方标识（关联 stream_push_client.client_id）；未开启鉴权时为空")
    private String clientId;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
