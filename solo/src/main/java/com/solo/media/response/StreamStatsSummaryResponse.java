package com.solo.media.response;

import lombok.Data;

/**
 * 节点流统计概要（弹窗首屏用）
 *
 * <p>对应 {@code GET /api/media-server/{serverId}/stream-stats/summary}。
 * 只返回 KPI 数字 + 节点身份信息，明细按 Tab 懒加载：
 * {@code GET /{serverId}/stream-stats/{gb|push|proxy|onvif}}。</p>
 *
 * <p>设计意图：弹窗打开时这个接口响应控制在 ~200B 以内，Tab 标签上的 count
 * 数字能立刻就位；明细只在用户点中对应 Tab 时才发请求。</p>
 *
 * <h3>字段语义</h3>
 * <ul>
 *   <li>{@link #gbStreamCount} / {@link #pushStreamCount} / {@link #proxyStreamCount} /
 *       {@link #onvifStreamCount} — 四类已建模会话的 count，弹窗 Tab 标签用</li>
 *   <li>{@link #total} — ZLM 视角的权威活跃流数（Redis ZSet 维护），
 *       理论上覆盖所有来源；与四类累加之和不一定相等</li>
 * </ul>
 */
@Data
public class StreamStatsSummaryResponse {

    /** 节点 serverId（与 {@code media_server.server_id} 一致） */
    private String serverId;

    /** 节点显示名（来自 {@code media_server.name}） */
    private String serverName;

    /** ZLM 管理面 IP（用于 REST API 调用） */
    private String hostIp;

    /** ZLM HTTP API 端口 */
    private Integer httpPort;

    /** 节点在线状态：0 离线，1 在线 */
    private Integer status;

    /** 国标点播 / 回放会话数（Redis 实时） */
    private Long gbStreamCount;

    /** 第三方推流数（MySQL） */
    private Long pushStreamCount;

    /** 拉流代理数（MySQL，仅 {@code status=1}） */
    private Long proxyStreamCount;

    /** ONVIF 实时点播会话数（Redis 实时） */
    private Long onvifStreamCount;

    /**
     * 该节点上的活跃流总数（ZLM {@code on_stream_changed} hook 维护在 Redis ZSet）
     *
     * <p>与 {@link #gbStreamCount} / {@link #pushStreamCount} / {@link #proxyStreamCount} /
     * {@link #onvifStreamCount} 之和 <b>不一定相等</b>——前者是 ZLM 视角的权威值，理论上覆盖
     * 所有来源；后者只覆盖四类已建模会话，未来新增来源时差值会先于 total 反映出来。</p>
     */
    private Long total;
}
