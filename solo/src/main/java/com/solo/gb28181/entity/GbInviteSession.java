package com.solo.gb28181.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 点播 / 回放 / 下载会话（<strong>Redis 主存</strong>，纯运行时状态，非历史记录）
 *
 * <p>生命周期：INVITING → READY → 任意原因结束 <strong>物理删除</strong>。</p>
 *
 * <p>真正的业务主键是 {@link #streamId}（=<code>deviceId_channelId</code>），
 * 没有自增 id；callId / ssrc / device+channel 也都建了反查索引（见
 * {@link com.solo.gb28181.service.impl.GbInviteSessionServiceImpl}）。</p>
 *
 * <p>真要做"播放历史 / 审计"请新建 {@code gb_play_history} MySQL 表，
 * schema 也不一样（只记 device/channel/start/end/duration/operator）。</p>
 */
@Schema(description = "点播 / 回放 / 下载会话（Redis 主存）")
@Data
public class GbInviteSession {

    public static final String STATUS_INVITING = "INVITING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_STOPPED = "STOPPED";
    public static final String STATUS_TIMEOUT = "TIMEOUT";
    public static final String STATUS_FAILED = "FAILED";

    public static final String TYPE_PLAY = "PLAY";
    public static final String TYPE_PLAYBACK = "PLAYBACK";
    public static final String TYPE_DOWNLOAD = "DOWNLOAD";

    /** 业务主键：ZLM stream 名（{@code <deviceId>_<channelId>}，回放再附时间段后缀） */
    @Schema(description = "业务流 ID")
    private String streamId;

    /** ZLM 节点 ID */
    @Schema(description = "承载流节点 ID")
    private String serverId;

    @Schema(description = "设备国标 ID")
    private String deviceId;

    @Schema(description = "通道国标 ID")
    private String channelId;

    /** 10 位 SSRC */
    @Schema(description = "SSRC")
    private String ssrc;

    /** ZLM openRtpServer 返回的端口 */
    @Schema(description = "RTP 端口")
    private Integer rtpPort;

    /** 收流模式：UDP / TCP-active / TCP-passive */
    @Schema(description = "传输模式")
    private String transport;

    /** 码流 token：空 / null 主码流（实时点播默认；回放固定 null），非空子码流（如 1 / stream:sub） */
    @Schema(description = "码流 token")
    private String streamNumber;

    @Schema(description = "SIP Call-ID")
    private String callId;

    @Schema(description = "SIP From-tag")
    private String fromTag;

    @Schema(description = "SIP To-tag")
    private String toTag;

    /** 会话类型 PLAY/PLAYBACK/DOWNLOAD */
    @Schema(description = "会话类型 PLAY/PLAYBACK/DOWNLOAD")
    private String sessionType;

    /** 回放起始（Unix epoch 字符串），实时点播留空 */
    @Schema(description = "回放起始时间")
    private String startTimeStr;
    /** 回放结束（Unix epoch 字符串） */
    @Schema(description = "回放结束时间")
    private String endTimeStr;

    /** INVITING/READY/STOPPED/TIMEOUT/FAILED */
    @Schema(description = "会话状态 INVITING/READY/STOPPED/TIMEOUT/FAILED")
    private String status;

    @Schema(description = "HTTP-FLV 播放地址")
    private String flvUrl;

    @Schema(description = "WS-FLV 播放地址")
    private String wsFlvUrl;

    @Schema(description = "HLS 播放地址")
    private String hlsUrl;

    @Schema(description = "WebRTC 播放地址")
    private String rtcUrl;

    @Schema(description = "会话开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Schema(description = "流就绪时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readyTime;

    @Schema(description = "会话停止时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime stopTime;

    @Schema(description = "失败原因")
    private String failReason;
}
