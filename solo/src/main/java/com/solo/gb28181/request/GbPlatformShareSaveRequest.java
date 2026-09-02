package com.solo.gb28181.request;

import lombok.Data;

import java.util.List;

/**
 * 上级平台共享资源保存请求
 * <p>对应 {@code POST /api/gb28181/parent-platform/{id}/shared}。</p>
 * <p>全量覆盖：{@code channelIds} 为前端 el-tree 勾选（含半选父节点）的带前缀节点 id，
 * 同一份契约同时承载分组（{@code g_} 前缀）和通道（{@code c_} 前缀）——按节点类型前缀分类入库。</p>
 */
@Data
public class GbPlatformShareSaveRequest {

    /**
     * 勾选的 catalog item 节点 id（前端 el-tree node-key）：
     * 分组 {@code g_xxx} / 通道 {@code c_xxx}，前端按节点类型派生前缀，后端按前缀分类入库。
     */
    private List<String> channelIds;
}
