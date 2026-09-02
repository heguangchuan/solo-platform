package com.solo.gb28181.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 点播开始响应
 *
 * <p>对应 {@code POST /api/gb28181/play/start}。</p>
 * <p>同时提供 {@code wsFlv} / {@code flv} / {@code hls} / {@code rtc} 多协议播放地址，前端按 player 能力挑一个用。
 * 截图功能由 jessibuca 播放器自带，不走后端。</p>
 */
@Schema(description = "点播开始响应")
@Data
public class PlayResultResponse {

    @Schema(description = "流 ID（约定 <deviceId>_<channelId>，停止点播时回传）")
    private String streamId;

    @Schema(description = "流应用名，固定 rtp")
    private String app;

    @Schema(description = "流媒体节点 ID")
    private String mediaServerId;

    @Schema(description = "RTP SSRC（10 位数字）")
    private String ssrc;

    @Schema(description = "RTP 收流端口")
    private Integer rtpPort;

    @Schema(description = "收流模式：UDP / TCP-passive")
    private String transport;

    @Schema(description = "码流 token：空 = 主码流，非空 = 子码流")
    private String streamNumber;

    @Schema(description = "HTTP-FLV 播放地址")
    private String flvUrl;

    @Schema(description = "WebSocket-FLV 播放地址（jessibuca 默认使用）")
    private String wsFlvUrl;

    @Schema(description = "HLS（m3u8）播放地址")
    private String hlsUrl;

    @Schema(description = "WebRTC 播放地址（节点未配置 rtcPort 时为空字符串）")
    private String rtcUrl;
}
