package com.solo.gb28181.response;

import lombok.Data;

import java.util.List;

/**
 * 共享目录树节点响应
 * <p>「上级平台 → 通道推送」弹窗用：资源目录分组树（gb_group 215/216 层级）作枝干，
 * 通道（gb_device_channel）按 group_id 挂到各自分组下作叶子。</p>
 * <p>节点 {@link #id} 带类型前缀避免分组主键与通道主键碰撞：
 * 分组 {@code g_<gb_group.id>}，通道 {@code c_<gb_device_channel.id>}。</p>
 */
@Data
public class GbShareTreeNodeResponse {

    /** 节点唯一键（前端 el-tree node-key）：分组 g_xxx / 通道 c_xxx */
    private String id;

    /** 节点显示标签 */
    private String label;

    /** 节点类型：GROUP 分组目录 / CHANNEL 通道 */
    private String type;

    /** 子节点 */
    private List<GbShareTreeNodeResponse> children;
}
