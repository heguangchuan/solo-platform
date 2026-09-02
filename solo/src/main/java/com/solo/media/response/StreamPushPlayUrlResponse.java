package com.solo.media.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 推流播放地址响应
 *
 * <p>对应 {@code GET /api/stream-push/{id}/play-urls}。
 * 推流在线时返回完整四类播放地址；推流已停止时所有 URL 字段为空字符串。
 * 字段定义与 {@code StreamProxyPlayUrlResponse} 保持一致，前端可复用播放器组件。</p>
 *
 * <h3>四类 URL 适用场景</h3>
 * <ul>
 *   <li>{@link #flvUrl} — HTTP-FLV，延迟低，主流浏览器配合 flv.js 即可播放</li>
 *   <li>{@link #wsFlvUrl} — WebSocket-FLV，jessibuca 默认使用，延迟同 HTTP-FLV</li>
 *   <li>{@link #hlsUrl} — HLS m3u8，Safari 原生支持，延迟 5~10 秒</li>
 *   <li>{@link #rtcUrl} — WebRTC，最低延迟（&lt;500ms），节点未配置 rtcPort 时为空</li>
 * </ul>
 */
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
