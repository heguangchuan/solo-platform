package com.solo.media.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "流媒体节点")
@Data
@TableName("media_server")
public class MediaServer {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "ZLM 节点唯一标识（mediaServerId）")
    private String serverId;

    @Schema(description = "节点显示名")
    private String name;

    @Schema(description = "ZLM 管理面 IP（用于 REST API 调用）")
    private String hostIp;

    @Schema(description = "ZLM HTTP API 端口")
    private Integer httpPort;

    @Schema(description = "ZLM API secret")
    private String secret;

    @Schema(description = "对外播放 IP；留空则同 hostIp")
    private String streamIp;

    @Schema(description = "对外 HTTP/WS-FLV/HLS 端口（≤0 时与 httpPort 同）")
    private Integer streamHttpPort;

    @Schema(description = "对外 RTSP 端口（≤0 时不生成 RTSP URL）")
    private Integer rtspPort;

    @Schema(description = "对外 WebRTC 端口（≤0 时不生成 WebRTC URL）")
    private Integer rtcPort;

    @Schema(description = "在线状态：0 离线，1 在线")
    private Integer status;

    @Schema(description = "最近一次 on_server_keepalive 时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime hookAliveTime;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
