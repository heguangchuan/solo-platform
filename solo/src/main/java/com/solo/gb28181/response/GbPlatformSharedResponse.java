package com.solo.gb28181.response;

import lombok.Data;

import java.util.List;

/**
 * 上级平台已共享的 catalog item 节点 id 集合
 * <p>回填 el-tree 勾选态用，元素为带前缀的节点 id（分组 g_xxx / 通道 c_xxx）。</p>
 */
@Data
public class GbPlatformSharedResponse {

    /**
     * 已共享的 catalog item 节点 id（前端 el-tree node-key）：
     * 分组 g_xxx（parental=1）/ 通道 c_xxx（parental=0），后端 JOIN gb_device_channel 派生前缀。
     */
    private List<String> channelIds;
}
