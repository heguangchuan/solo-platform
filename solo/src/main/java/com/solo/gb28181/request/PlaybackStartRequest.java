package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 开始回放请求
 *
 * <p>对应 {@code POST /api/gb28181/playback/start}。回放成功后返回 {@link com.solo.gb28181.response.PlaybackResultResponse}。</p>
 */
@Schema(description = "开始回放请求")
@Data
public class PlaybackStartRequest {

    @Schema(description = "设备国标 20 位 ID")
    private String deviceId;

    @Schema(description = "通道国标 20 位 ID")
    private String channelId;

    @Schema(description = "起始时间 yyyy-MM-dd HH:mm:ss")
    private String startTime;

    @Schema(description = "结束时间 yyyy-MM-dd HH:mm:ss")
    private String endTime;

    @Schema(description = "收流模式：UDP / TCP-passive / TCP-active")
    private String transport = "TCP-passive";

    @Schema(description = "码流 token：空 = 主码流，非空 = 子码流")
    private String streamNumber;
}
