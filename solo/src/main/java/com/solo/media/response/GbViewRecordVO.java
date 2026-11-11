package com.solo.media.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Schema(description = "观看记录视图")
@Data
public class GbViewRecordVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String sessionId;

    @Schema(description = "观看者类型 1平台用户 2开放API应用")
    private Integer actorType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long actorId;

    private String actorName;

    private String deviceId;

    private String deviceName;

    private String channelId;

    private String channelName;

    private String streamId;

    private String app;

    @Schema(description = "播放协议（rtsp/rtmp/hls/fmp4/rtc）")
    private String streamSchema;

    private String clientIp;

    private String mediaServerId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @Schema(description = "观看时长（秒），观看中为空")
    private Double durationSec;

    @Schema(description = "会话总字节")
    private Long totalBytes;

    @Schema(description = "状态 0观看中 1正常结束 2兜底关闭")
    private Integer status;

    private String closeReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
