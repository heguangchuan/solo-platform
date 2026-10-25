package com.solo.openapi.controller.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 点播开始结果。播放地址直接给全，第三方拿了就能播，不用自己拼。
 */
@Schema(description = "点播开始结果")
public record OpenPlayResultVO(
        @Schema(description = "流 ID，停止点播时回传") String streamId,
        @Schema(description = "收流模式：UDP / TCP-passive") String transport,
        @Schema(description = "HTTP-FLV 播放地址") String flvUrl,
        @Schema(description = "WebSocket-FLV 播放地址") String wsFlvUrl,
        @Schema(description = "HLS（m3u8）播放地址") String hlsUrl,
        @Schema(description = "WebRTC 播放地址（节点未开 rtc 时为空串）") String rtcUrl) {
}
