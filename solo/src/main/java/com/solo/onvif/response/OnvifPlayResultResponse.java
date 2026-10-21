package com.solo.onvif.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "ONVIF 点播开始响应")
@Data
public class OnvifPlayResultResponse {

    @Schema(description = "ZLM 流名（约定 onvif_<deviceId>），stop 时原样回传")
    private String streamId;

    @Schema(description = "ZLM 应用名，固定 rtsp（与国标 rtp 区分）")
    private String app;

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
