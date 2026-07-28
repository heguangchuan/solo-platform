package com.solo.gb28181.request;

import lombok.Data;

/**
 * 上级平台分页查询请求
 */
@Data
public class GbParentPlatformPageRequest {

    private Long page = 1L;
    private Long pageSize = 20L;

    /**
     * 上级平台名称（模糊）
     */
    private String name;

    /**
     * 上级平台国标编码（模糊）
     */
    private String serverGbId;

    /**
     * 启用状态：0 禁用 1 启用
     */
    private Integer enabled;
}
