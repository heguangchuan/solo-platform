package com.solo.onvif.response;

import lombok.Data;

/**
 * ONVIF 点播开始响应
 * <p>与国标 {@code PlayResultResponse} 字段对齐，前端播放器组件可复用。</p>
 */
@Data
public class OnvifPlayResultResponse {

    /** ZLM stream（约定 {@code onvif_<deviceId>}） */
    private String streamId;

    /** ZLM app，固定 {@code rtsp}（与国标 {@code rtp} 区分） */
    private String app;

    /** ZLM 节点 ID */
    private String mediaServerId;

    /** HTTP-FLV 地址 */
    private String flvUrl;

    /** WebSocket-FLV 地址（jessibuca 默认使用） */
    private String wsFlvUrl;

    /** HLS m3u8 地址 */
    private String hlsUrl;

    /** WebRTC 播放地址（节点未配置 rtcPort 时为空） */
    private String rtcUrl;
}
