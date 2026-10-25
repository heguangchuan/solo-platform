package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "语音广播 WebRTC SDP 交换请求")
@Data
public class BroadcastWebRtcRequest {

    @Schema(description = "广播流 ID（开始广播时返回）")
    private String streamId;

    @Schema(description = "浏览器生成的 offer SDP")
    private String sdp;
}
