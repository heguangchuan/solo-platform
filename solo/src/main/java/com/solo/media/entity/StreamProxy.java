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
 * 拉流代理实体
 * <p>用户在管理台建的第三方源（RTSP/RTMP/HTTP-FLV/HLS 等）拉流代理配置。</p>
 */
@Data
@TableName("stream_proxy")
public class StreamProxy {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** ZLM app 名，默认 proxy（与国标 rtp、ONVIF rtsp 区分开） */
    private String app;

    /** ZLM stream 名 */
    private String stream;

    /** 源地址：rtsp:// rtmp:// http-flv/hls 等 */
    private String srcUrl;

    /** 目标 ZLM 节点 serverId；留空则启动时挑一个可用节点，回填此字段 */
    private String mediaServerId;

    /** RTSP 拉流方式：0=TCP+UDP 自适应 1=TCP 2=UDP 3=组播 */
    private Integer rtpType;

    /** ZLM addStreamProxy 返回的 key，用于 delStreamProxy */
    private String proxyKey;

    /** 运行状态 0未启动 1已启动 */
    private Integer status;

    /** 最近一次启动失败原因（仅用于展示） */
    private String lastError;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
