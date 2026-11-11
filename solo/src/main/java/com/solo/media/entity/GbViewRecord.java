package com.solo.media.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Schema(description = "观看记录")
@Data
@TableName("gb_view_record")
public class GbViewRecord {

    /** 观看者：平台用户 */
    public static final int ACTOR_USER = 1;
    /** 观看者：开放 API 应用 */
    public static final int ACTOR_OPEN_APP = 2;

    /** 观看中（on_play 已落库，等 on_flow_report 闭合） */
    public static final int STATUS_OPEN = 0;
    /** 正常结束（on_flow_report 闭合） */
    public static final int STATUS_CLOSED = 1;
    /** 兜底关闭（流断 / none_reader / 定时清扫，没等到 flow_report） */
    public static final int STATUS_FALLBACK_CLOSED = 2;

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "ZLM 播放会话 id，幂等键")
    private String sessionId;

    @Schema(description = "观看者类型 1平台用户 2开放API应用")
    private Integer actorType;

    @Schema(description = "观看者 ID（用户 ID 或开放应用 ID）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long actorId;

    @Schema(description = "观看者名称快照")
    private String actorName;

    @Schema(description = "设备国标 ID")
    private String deviceId;

    @Schema(description = "通道国标 ID")
    private String channelId;

    @Schema(description = "ZLM 流 ID")
    private String streamId;

    @Schema(description = "ZLM app 名")
    private String app;

    @Schema(description = "播放协议（rtsp/rtmp/hls/fmp4/rtc）")
    private String streamSchema;

    @Schema(description = "观看端 IP")
    private String clientIp;

    @Schema(description = "ZLM 节点 ID")
    private String mediaServerId;

    @Schema(description = "观看开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Schema(description = "观看结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @Schema(description = "观看时长（秒）")
    private Double durationSec;

    @Schema(description = "会话总字节（on_flow_report 的 totalBytes）")
    private Long totalBytes;

    @Schema(description = "状态 0观看中 1正常结束 2兜底关闭")
    private Integer status;

    @Schema(description = "关闭原因 flow_report/stream_closed/none_reader/sweep")
    private String closeReason;

    @Schema(description = "入库时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
