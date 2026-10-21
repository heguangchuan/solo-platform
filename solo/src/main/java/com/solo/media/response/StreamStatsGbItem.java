package com.solo.media.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "节点流统计 - 国标会话明细项")
@Data
public class StreamStatsGbItem {

    @Schema(description = "业务主键：<deviceId>_<channelId>（回放再附时间段后缀），可用于断流接口的入参")
    private String streamId;

    @Schema(description = "设备国标 ID（20 位数字）")
    private String deviceId;

    @Schema(description = "通道国标 ID（20 位数字）")
    private String channelId;

    @Schema(description = "会话类型：PLAY（实时） / PLAYBACK（回放） / DOWNLOAD（下载）")
    private String sessionType;

    @Schema(description = "会话状态：INVITING / READY / STOPPED / TIMEOUT / FAILED")
    private String status;

    @Schema(description = "会话开始时间，yyyy-MM-dd HH:mm:ss 格式，表格直接展示")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
}
