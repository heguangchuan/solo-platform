package com.solo.media.response;

import com.solo.gb28181.entity.GbInviteSession;
import com.solo.media.entity.StreamProxy;
import com.solo.media.entity.StreamPush;
import com.solo.onvif.entity.OnvifSession;
import lombok.Data;

import java.util.List;

/**
 * 节点流统计详情（弹窗下钻用）
 *
 * <p>对应 {@code GET /api/media-server/{serverId}/stream-stats}，由节点列表的"流统计"
 * 按钮触发。一次性返回该节点上的四类会话的 <strong>KPI 总数 + 明细列表</strong>：</p>
 * <ul>
 *     <li>{@link #gbStreamCount} / {@link #gbSessions} — 国标点播/回放会话（Redis 实时）</li>
 *     <li>{@link #pushStreamCount} / {@link #pushes} — 第三方推流（MySQL）</li>
 *     <li>{@link #proxyStreamCount} / {@link #proxies} — 拉流代理（MySQL，仅 status=1）</li>
 *     <li>{@link #onvifStreamCount} / {@link #onvifSessions} — ONVIF 实时点播会话（Redis 实时）</li>
 * </ul>
 *
 * <p>{@link #total} 来自 ZLM 的 {@code on_stream_changed} hook → Redis ZSet 节点分数，
 * 是该节点上"全部活跃流"的权威值——<b>不是</b> {@code gb + push + proxy + onvif} 的简单累加，
 * 否则会漏掉未来其他来源。四类明细的累加值通常会 ≤ total。</p>
 *
 * <p>四类的明细直接复用现有实体（{@link GbInviteSession} / {@link StreamPush} / {@link StreamProxy} /
 * {@link OnvifSession}），不再额外定义 VO——避免重复定义字段映射。</p>
 */
@Data
public class MediaServerStreamStatsResponse {

    /** 节点 serverId（与 {@code media_server.server_id} 一致） */
    private String serverId;

    /** 节点显示名（来自 media_server.name） */
    private String serverName;

    /** ZLM 管理面 IP */
    private String hostIp;

    /** ZLM HTTP API 端口 */
    private Integer httpPort;

    /** 节点在线状态：0 离线，1 在线 */
    private Integer status;

    /** 国标点播/回放会话数 */
    private Long gbStreamCount;

    /** 第三方推流数 */
    private Long pushStreamCount;

    /** 拉流代理数（status=1） */
    private Long proxyStreamCount;

    /** ONVIF 实时点播会话数 */
    private Long onvifStreamCount;

    /**
     * 该节点上的活跃流总数（ZLM on_stream_changed hook 维护在 Redis ZSet）
     *
     * <p>与 {@link #gbStreamCount} / {@link #pushStreamCount} / {@link #proxyStreamCount} /
     * {@link #onvifStreamCount} 之和 <b>不一定相等</b>——前者是 ZLM 视角的权威值，理论上覆盖
     * 所有来源；后者只覆盖四类已建模会话，未来新增来源时差值会先于 total 反映出来。</p>
     */
    private Long total;

    /** 国标会话明细（可能为空） */
    private List<GbInviteSession> gbSessions;

    /** 第三方推流明细（可能为空） */
    private List<StreamPush> pushes;

    /** 拉流代理明细（仅 status=1，可能为空） */
    private List<StreamProxy> proxies;

    /** ONVIF 会话明细（可能为空） */
    private List<OnvifSession> onvifSessions;
}
