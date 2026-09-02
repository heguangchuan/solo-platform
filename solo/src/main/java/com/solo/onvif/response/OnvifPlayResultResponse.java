package com.solo.onvif.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * ONVIF 点播开始响应
 *
 * <p>对应 {@code POST /api/onvif/play/start}。
 * 字段定义与国标 {@code PlayResultResponse} 对齐，前端播放器组件可复用同一渲染逻辑。
 * 拿到本响应后，前端可直接用 {@link #flvUrl} / {@link #wsFlvUrl} / {@link #hlsUrl} / {@link #rtcUrl}
 * 投递给播放器；调用 {@code /stop} 时把 {@link #streamId} 原样回传。</p>
 *
 * <h3>四类 URL 适用场景</h3>
 * <ul>
 *   <li>{@link #flvUrl} — HTTP-FLV，低延迟，配合 flv.js 播放</li>
 *   <li>{@link #wsFlvUrl} — WebSocket-FLV，jessibuca 默认使用，延迟同 HTTP-FLV</li>
 *   <li>{@link #hlsUrl} — HLS m3u8，Safari 原生支持，延迟 5~10 秒</li>
 *   <li>{@link #rtcUrl} — WebRTC，最低延迟（&lt;500ms），节点未配置 rtcPort 时为空</li>
 * </ul>
 */
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
