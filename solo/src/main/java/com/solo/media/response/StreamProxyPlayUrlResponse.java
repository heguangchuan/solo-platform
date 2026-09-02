package com.solo.media.response;

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
@Data
public class StreamProxyPlayUrlResponse {

    /** ZLM 应用名（默认 {@code proxy}） */
    private String app;

    /** ZLM 流名（一般同源 stream） */
    private String stream;

    /** ZLM 节点 serverId，标识该流在哪个节点 */
    private String mediaServerId;

    /** HTTP-FLV 播放地址 */
    private String flvUrl;

    /** WebSocket-FLV 播放地址 */
    private String wsFlvUrl;

    /** HLS m3u8 地址 */
    private String hlsUrl;

    /** WebRTC 播放地址（节点未配置 rtcPort 时为空） */
    private String rtcUrl;
}
