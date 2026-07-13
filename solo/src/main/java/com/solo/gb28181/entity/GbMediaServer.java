package com.solo.gb28181.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 流媒体节点实体（对应 ZLMediaKit 实例）
 * <p>{@link #serverId} 必须与 ZLM config.ini 中 [general].mediaServerId 一致，
 * 否则 ZLM 发回的 hook 回调（params.mediaServerId）将匹配不到任何节点。</p>
 */
@Data
@TableName("gb_media_server")
public class GbMediaServer {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * ZLM 节点唯一标识（mediaServerId）
     */
    private String serverId;

    /**
     * 节点显示名
     */
    private String name;

    /**
     * ZLM 管理面 IP（用于 REST API 调用）
     */
    private String hostIp;

    /**
     * ZLM HTTP API 端口
     */
    private Integer httpPort;

    /**
     * ZLM API secret
     */
    private String secret;

    /**
     * 对外播放 IP；留空则同 hostIp
     */
    private String streamIp;

    /**
     * 对外 HTTP/WS-FLV/HLS 端口（≤0 时与 {@link #httpPort} 同）
     */
    private Integer streamHttpPort;

    /**
     * 对外 RTSP 端口（≤0 时不生成 RTSP URL）
     */
    private Integer rtspPort;

    /**
     * 对外 WebRTC 端口（≤0 时不生成 WebRTC URL）
     */
    private Integer rtcPort;

    /**
     * RTP 端口段起始
     */
    private Integer rtpPortStart;

    /**
     * RTP 端口段截止
     */
    private Integer rtpPortEnd;

    /**
     * 在线状态：0 离线，1 在线
     */
    private Integer status;

    /**
     * 最近一次 on_server_keepalive 时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime hookAliveTime;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
