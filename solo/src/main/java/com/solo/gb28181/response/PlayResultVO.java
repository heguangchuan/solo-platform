package com.solo.gb28181.response;

import lombok.Data;

/**
 * 点播开始响应
 * <p>同时提供 wsFlv / flv / hls / rtc / snap 等多协议地址，前端按 player 能力挑一个用。</p>
 */
@Data
public class PlayResultVO {

    /**
     * ZLM stream（约定 {@code <deviceId>_<channelId>}）
     */
    private String streamId;

    /**
     * ZLM app，固定 {@code rtp}
     */
    private String app;

    /**
     * ZLM 节点 ID
     */
    private String mediaServerId;

    /**
     * 10 位 SSRC
     */
    private String ssrc;

    /**
     * ZLM 实际占用的 RTP 端口
     */
    private Integer rtpPort;

    /**
     * 收流模式：{@code UDP / TCP-passive}
     */
    private String transport;

    /**
     * HTTP-FLV 地址
     */
    private String flvUrl;

    /**
     * WebSocket-FLV 地址（jessibuca 默认使用）
     */
    private String wsFlvUrl;

    /**
     * HLS m3u8 地址
     */
    private String hlsUrl;

    /**
     * WebRTC 播放地址（节点未配置 rtcPort 时为空）
     */
    private String rtcUrl;

    /**
     * 截图地址（前端「截图」按钮触发后返回的访问 URL）
     */
    private String snapUrl;
}
