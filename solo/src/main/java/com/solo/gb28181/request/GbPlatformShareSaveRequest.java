package com.solo.gb28181.request;

import lombok.Data;

import java.util.List;

/**
 * 上级平台通道推送（共享）保存请求
 * <p>全量覆盖：groupIds / channelIds 为前端 el-tree 勾选（含半选父节点）的带前缀节点 id。</p>
 */
@Data
public class GbPlatformShareSaveRequest {

    /** 勾选的分组节点 id（g_ 前缀） */
    private List<String> groupIds;

    /** 勾选的通道节点 id（c_ 前缀） */
    private List<String> channelIds;
}
