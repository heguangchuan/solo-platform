package com.solo.gb28181.response;

import lombok.Data;

import java.util.List;

/**
 * 共享目录树节点响应
 * <p>「上级平台 → 通道推送」弹窗用：catalog item 树（parental=1 分组作枝干，
 * parental=0 通道作叶子）。</p>
 * <p>节点 {@link #id} 带类型前缀避免分组主键与通道主键碰撞：
 * 分组 {@code g_<gb_device_channel.id>}，通道 {@code c_<gb_device_channel.id>}。</p>
 */
@Data
public class GbShareTreeNodeResponse {

    /** 节点唯一键（前端 el-tree node-key）：分组 g_xxx / 通道 c_xxx */
    private String id;

    /** 节点显示标签 */
    private String label;

    /** 节点类型：GROUP 分组（parental=1）/ CHANNEL 通道（parental=0） */
    private String type;

    /** 子节点 */
    private List<GbShareTreeNodeResponse> children;
}
