package com.solo.gb28181.response;

import lombok.Data;

import java.util.List;

/**
 * 上级平台已共享的目录 / 通道节点 id 集合
 * <p>回填 el-tree 勾选态用，元素为带前缀的节点 id（分组 g_xxx / 通道 c_xxx）。</p>
 */
@Data
public class GbPlatformSharedResponse {

    /** 已共享的分组节点 id（g_ 前缀） */
    private List<String> groupIds;

    /** 已共享的通道节点 id（c_ 前缀） */
    private List<String> channelIds;
}
