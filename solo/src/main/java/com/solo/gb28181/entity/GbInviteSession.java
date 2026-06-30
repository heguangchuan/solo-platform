package com.solo.gb28181.entity;

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
 * 点播 / 回放 / 下载会话实体（运行时状态表，<strong>非</strong>历史记录）
 * <p>一次 INVITE 流程产生一行；通过 {@link #ssrc} 与 ZLM 的 stream（约定为
 * {@code rtp/<deviceId>_<channelId>}）双向关联。生命周期：
 * INVITING → READY → STOPPED；超时/失败置 TIMEOUT/FAILED。</p>
 * <p>会话结束（stop / timeout / fail / 设备 BYE）后 <strong>物理删除</strong>，
 * 不保留逻辑删除痕迹 —— 真要做播放历史/审计请走独立表，schema 不一样。</p>
 */
@Data
@TableName("gb_invite_session")
public class GbInviteSession {

    public static final String STATUS_INVITING = "INVITING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_STOPPED = "STOPPED";
    public static final String STATUS_TIMEOUT = "TIMEOUT";
    public static final String STATUS_FAILED = "FAILED";

    public static final String TYPE_PLAY = "PLAY";
    public static final String TYPE_PLAYBACK = "PLAYBACK";
    public static final String TYPE_DOWNLOAD = "DOWNLOAD";

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * ZLM stream 名（约定 {@code rtp/<deviceId>_<channelId>}，回放再附时间段后缀）
     */
    private String streamId;

    /**
     * ZLM 节点 ID
     */
    private String serverId;

    private String deviceId;
    private String channelId;

    /**
     * 10 位 SSRC（十进制字符串，国标 SDP 的 y= 行）
     */
    private String ssrc;

    /**
     * ZLM openRtpServer 返回的端口
     */
    private Integer rtpPort;

    /**
     * 收流模式：UDP / TCP-active / TCP-passive
     */
    private String transport;

    private String callId;
    private String fromTag;
    private String toTag;

    /**
     * 会话类型 PLAY/PLAYBACK/DOWNLOAD
     */
    private String sessionType;

    /**
     * 回放起始（Unix epoch 字符串），实时点播留空
     */
    private String startTimeStr;
    /**
     * 回放结束（Unix epoch 字符串）
     */
    private String endTimeStr;

    /**
     * INVITING/READY/STOPPED/TIMEOUT/FAILED
     */
    private String status;

    private String flvUrl;
    private String wsFlvUrl;
    private String hlsUrl;
    private String rtcUrl;
    private String snapUrl;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readyTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime stopTime;

    private String failReason;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
