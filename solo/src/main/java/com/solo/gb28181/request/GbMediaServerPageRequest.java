package com.solo.gb28181.request;

import lombok.Data;

/**
 * 流媒体节点分页查询请求
 */
@Data
public class GbMediaServerPageRequest {

    private Long page = 1L;
    private Long pageSize = 20L;

    /**
     * 节点 serverId（模糊）
     */
    private String serverId;

    /**
     * 在线状态：0 离线，1 在线
     */
    private Integer status;
}
