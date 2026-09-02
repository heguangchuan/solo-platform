package com.solo.media.response;

import lombok.Data;

/**
 * 节点流统计 - 拉流代理明细项
 *
 * <p>对应 {@code GET /api/media-server/{serverId}/stream-stats/proxy} 列表元素。
 * 字段为管理台弹窗表格所需最小集（{@code app / stream / srcUrl / status}），
 * 不复用 {@code StreamProxy} 实体以减少 JSON 体积。</p>
 *
 * <p>当前接口只返回 {@code status=1}（已启动）的代理行；{@code status=0} 的不会出现在列表里。</p>
 */
@Data
public class StreamStatsProxyItem {

    /** ZLM app 名（默认 {@code proxy}，与国标 rtp、ONVIF rtsp 区分） */
    private String app;

    /** ZLM stream 名 */
    private String stream;

    /** 源地址：{@code rtsp://} / {@code rtmp://} / {@code http-flv} / {@code hls} 等 */
    private String srcUrl;

    /** 运行状态：0 未启动 1 已启动（当前接口只返回 {@code status=1}） */
    private Integer status;
}
