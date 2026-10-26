package com.solo.gb28181.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "语音广播开始响应")
@Data
public class BroadcastResultResponse {

    @Schema(description = "广播流 ID（= ZLM app=broadcast 下的 stream 名，停止时回传）")
    private String streamId;

    @Schema(description = "流应用名，固定 broadcast")
    private String app;

    @Schema(description = "流媒体节点 ID")
    private String mediaServerId;

    @Schema(description = "浏览器 WebRTC 推流地址（type=push，POST SDP 用）")
    private String rtcPushUrl;

    @Schema(description = "双向对讲时才有：浏览器 WebRTC 拉流地址（type=play，听设备端音频用，app=rtp）")
    private String rtcPlayUrl;
}
