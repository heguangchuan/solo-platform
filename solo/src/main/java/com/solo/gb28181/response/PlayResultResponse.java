package com.solo.gb28181.response;

import lombok.Data;

/**
 * 点播开始响应
 *
 * <p>对应 {@code POST /api/gb28181/play/start}。</p>
 * <p>同时提供 {@code wsFlv} / {@code flv} / {@code hls} / {@code rtc} 多协议播放地址，前端按 player 能力挑一个用。
 * 截图功能由 jessibuca 播放器自带，不走后端。</p>
 */
@Data
public class PlayResultResponse {

    /**
     * 流 ID（约定 {@code <deviceId>_<channelId>}，停止点播时回传）
     */
    private String streamId;

    /** 流应用名，固定 {@code rtp} */
    private String app;

    /** 流媒体节点 ID */
    private String mediaServerId;

    /** 10 位 SSRC（同步源标识） */
    private String ssrc;

    /** 流媒体实际占用的 RTP 端口 */
    private Integer rtpPort;

    /** 收流模式：{@code UDP} / {@code TCP-passive} */
    private String transport;

    /** 码流 token：空 = 主码流，非空 = 子码流。前端切换后据此回显当前档位。 */
    private String streamNumber;

    /** HTTP-FLV 播放地址 */
    private String flvUrl;

    /** WebSocket-FLV 播放地址（jessibuca 默认使用） */
    private String wsFlvUrl;

    /** HLS（m3u8）播放地址 */
    private String hlsUrl;

    /** WebRTC 播放地址（节点未配置 rtcPort 时为空字符串） */
    private String rtcUrl;
}
