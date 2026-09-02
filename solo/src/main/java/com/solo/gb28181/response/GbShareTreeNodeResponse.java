package com.solo.gb28181.response;

import lombok.Data;

import java.util.List;

/**
 * 共享目录树节点响应
 *
 * <p>对应 {@code GET /api/gb28181/parent-platform/share-tree}。</p>
 * <p>"上级平台 → 通道推送"弹窗用三级树：<b>设备节点</b>（枝干，不可勾选）→
 * <b>分组</b> → <b>通道</b>。</p>
 * <p>节点 {@link #id} 带类型前缀避免主键碰撞：设备 {@code d_<device_id>}（GB 码）、
 * 分组 {@code g_<dbId>}、通道 {@code c_<dbId>}。
 * 只有 {@code g_}/{@code c_} 前缀节点会被提交到保存接口，{@code d_} 前缀仅作展示。</p>
 */
@Data
public class GbShareTreeNodeResponse {

    /** 节点唯一键（前端 el-tree node-key）：设备 {@code d_xxx} / 分组 {@code g_xxx} / 通道 {@code c_xxx} */
    private String id;

    /** 节点显示标签 */
    private String label;

    /** 节点类型：{@code DEVICE} 设备（枝干） / {@code GROUP} 分组 / {@code CHANNEL} 通道 */
    private String type;

    /** 子节点 */
    private List<GbShareTreeNodeResponse> children;
}
