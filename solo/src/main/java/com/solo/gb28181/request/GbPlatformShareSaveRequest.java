package com.solo.gb28181.request;

import lombok.Data;

import java.util.List;

/**
 * 上级平台共享资源保存请求
 * <p>全量覆盖：{@code channelIds} 为前端 el-tree 勾选（含半选父节点）的带前缀节点 id，
 * 同一份契约同时承载分组（g_ 前缀）和通道（c_ 前缀）——ADR-023 合并后分组也是 catalog item，
 * 后端按前缀分类落到同一张 {@code gb_platform_share}。</p>
 */
@Data
public class GbPlatformShareSaveRequest {

    /**
     * 勾选的 catalog item 节点 id（前端 el-tree node-key）：
     * 分组 g_xxx / 通道 c_xxx，前端按 parental 派生前缀，后端按前缀分类入库。
     */
    private List<String> channelIds;
}
