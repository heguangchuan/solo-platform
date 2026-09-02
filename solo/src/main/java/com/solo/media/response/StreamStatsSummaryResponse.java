package com.solo.media.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 节点流统计概要（弹窗首屏用）
 *
 * <p>对应 {@code GET /api/media-server/{serverId}/stream-stats/summary}。
 * 只返回节点身份 + 4 类会话的 count，响应体小，弹窗打开瞬间 Tab 上的数字就能就位；
 * 4 个 Tab 的会话明细按需懒加载，切换到哪个 Tab 才请求对应明细接口。</p>
 *
 * <h3>字段语义</h3>
 * <ul>
 *   <li>{@link #gbStreamCount} / {@link #pushStreamCount} / {@link #proxyStreamCount} /
 *       {@link #onvifStreamCount} — 四类已建模会话的 count，弹窗 Tab 标签用</li>
 *   <li>{@link #total} — 流媒体服务器视角的活跃流总数，理论上覆盖所有来源；
 *       与四类累加之和不一定相等（差异可能是未建模的临时流）</li>
 * </ul>
 *
 * <h3>弹窗使用流程</h3>
 * <ol>
 *   <li>打开弹窗时调 summary，4 个 Tab 标签上的数字立即就位</li>
 *   <li>用户点击某个 Tab，才请求对应明细接口（gb / push / proxy / onvif）</li>
 *   <li>弹窗关闭不再轮询，下次打开重新拉 summary</li>
 * </ol>
 */
@Schema(description = "节点流统计概要（弹窗首屏用）")
@Data
public class StreamStatsSummaryResponse {

    @Schema(description = "节点 serverId（管理台展示用）")
    private String serverId;

    @Schema(description = "节点显示名（管理台列表的同名字段）")
    private String serverName;

    @Schema(description = "流媒体服务器管理面 IP")
    private String hostIp;

    @Schema(description = "HTTP API 端口")
    private Integer httpPort;

    @Schema(description = "节点在线状态：0 离线，1 在线；前端可按值给 status 标签染色（绿 / 红）")
    private Integer status;

    @Schema(description = "国标点播 / 回放会话数")
    private Long gbStreamCount;

    @Schema(description = "第三方推流数")
    private Long pushStreamCount;

    @Schema(description = "拉流代理数（仅统计已启动的代理）")
    private Long proxyStreamCount;

    @Schema(description = "ONVIF 实时点播会话数")
    private Long onvifStreamCount;

    /**
     * 该节点上的活跃流总数（流媒体服务器视角）
     *
     * <p>与 {@link #gbStreamCount} / {@link #pushStreamCount} / {@link #proxyStreamCount} /
     * {@link #onvifStreamCount} 之和 <b>不一定相等</b>——前者覆盖流媒体服务器上的所有活跃流，
     * 后者只覆盖四类已建模会话；新增流来源时差值会先于 total 反映出来。</p>
     */
    @Schema(description = "该节点上的活跃流总数（流媒体服务器视角）")
    private Long total;
}
