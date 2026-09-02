package com.solo.media.response;

import lombok.Data;

/**
 * 节点流统计 - 拉流代理明细项
 *
 * <p>对应 {@code GET /api/media-server/{serverId}/stream-stats/proxy} 列表元素。
 * 字段为管理台弹窗表格所需最小集（{@code app / stream / srcUrl / status}），
 * 已剥离 StreamProxy 实体中前端用不到的内部字段，减少响应体积。</p>
 *
 * <p>当前接口只返回 {@code status=1}（已启动）的代理行；{@code status=0} 的不会出现在列表里，
 * 所以本接口里 status 字段实际恒为 1，保留仅为语义清晰。</p>
 */
@Data
public class StreamStatsProxyItem {

    /** ZLM 应用名（默认 {@code proxy}，与国标 rtp、ONVIF rtsp 区分） */
    private String app;

    /** ZLM 流名 */
    private String stream;

    /**
     * 源拉流地址
     *
     * <p>形如 {@code rtsp://user:pass@host:554/...} / {@code rtmp://...} /
     * {@code http://.../xxx.flv} / {@code http://.../xxx.m3u8}。
     * 展示时建议脱敏：{@code rtsp://user:pass@} → {@code rtsp://***@}，避免账号密码泄露。</p>
     */
    private String srcUrl;

    /** 运行状态：0 未启动 1 已启动（当前接口只返回 {@code status=1}） */
    private Integer status;
}
