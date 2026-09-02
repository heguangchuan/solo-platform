package com.solo.media.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 拉流代理播放地址响应
 *
 * <p>对应 {@code GET /api/stream-proxy/{id}/play-urls}。
 * 当代理处于"已启动"状态时返回完整四类播放地址；未启动时本类所有 URL 字段为空字符串，
 * 表格可据此显示"未启动"标签。</p>
 *
 * <h3>四类 URL 适用场景</h3>
 * <ul>
 *   <li>{@link #flvUrl} — HTTP-FLV，延迟低、兼容性好；适合内网主流浏览器（Chrome / Edge / Firefox），
 *       需配合 MSE 或 flv.js 库</li>
 *   <li>{@link #wsFlvUrl} — WebSocket-FLV，比 HTTP-FLV 更适合穿透反向代理与负载均衡，
 *       jessibuca 默认使用，延迟同 HTTP-FLV</li>
 *   <li>{@link #hlsUrl} — HLS m3u8，原生支持 iOS Safari；延迟 5~10 秒，不适合实时监控</li>
 *   <li>{@link #rtcUrl} — WebRTC，最低延迟（&lt;500ms），适合远程控制与互动场景；
 *       节点未配置 rtcPort 时返回空字符串，UI 需隐藏 RTC 选项</li>
 * </ul>
 */
@Schema(description = "拉流代理播放地址响应")
@Data
public class StreamProxyPlayUrlResponse {

    @Schema(description = "ZLM 应用名（默认 proxy）")
    private String app;

    @Schema(description = "ZLM 流名（一般同源 stream）")
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
