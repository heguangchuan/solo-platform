package com.solo.gb28181.request;

import lombok.Data;

/**
 * 下级级联平台分页查询请求
 */
@Data
public class GbPlatformPageRequest {

    private Long page = 1L;
    private Long pageSize = 20L;

    /**
     * 平台国标 ID（模糊）
     */
    private String platformId;

    /**
     * 平台名称（模糊）
     */
    private String name;

    /**
     * 启用状态：0 禁用 1 启用
     */
    private Integer enabled;
}