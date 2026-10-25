package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "语音广播开始请求（网页麦克风实时喊话）")
@Data
public class BroadcastStartRequest {

    @Schema(description = "设备国标 20 位 ID")
    private String deviceId;

    @Schema(description = "放音通道国标 20 位 ID")
    private String channelId;
}
