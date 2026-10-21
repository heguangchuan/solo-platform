package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "实时点播开始请求")
@Data
public class PlayStartRequest {

    @Schema(description = "设备国标 20 位 ID")
    private String deviceId;

    @Schema(description = "通道国标 20 位 ID")
    private String channelId;

    @Schema(description = "收流模式：UDP / TCP-passive")
    private String transport = "TCP-passive";

    @Schema(description = "码流标识 token：留空/null = 主码流（子码流默认 streamnumber:1）")
    private String streamNumber = "streamnumber:1";
}
