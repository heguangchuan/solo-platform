package com.solo.media.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "推流播放地址响应")
@Data
public class StreamPushPlayUrlResponse {

    @Schema(description = "ZLM 应用名（默认 live）")
    private String app;

    @Schema(description = "ZLM 流名（推流方自定义）")
    private String stream;

    @Schema(description = "ZLM 节点 serverId，标识该流在哪个节点")
    private String mediaServerId;

    @Schema(description = "HTTP-FLV 播放地址（HTTP-FLV 适合内网，HLS 适合 Safari，WebRTC 最低延迟）")
    private String flvUrl;

    @Schema(description = "WS-FLV 播放地址（HTTP-FLV 适合内网，HLS 适合 Safari，WebRTC 最低延迟）")
    private String wsFlvUrl;

    @Schema(description = "HLS 播放地址（HTTP-FLV 适合内网，HLS 适合 Safari，WebRTC 最低延迟）")
    private String hlsUrl;

    @Schema(description = "WebRTC 播放地址（HTTP-FLV 适合内网，HLS 适合 Safari，WebRTC 最低延迟）")
    private String rtcUrl;
}
