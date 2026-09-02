package com.solo.gb28181.request;

import lombok.Data;

/**
 * 上级平台分页查询请求
 *
 * <p>对应 {@code POST /api/gb28181/parent-platform/page}。分页参数：{@code page} 从 1 开始，默认 1；{@code pageSize} 默认 20。
 * 响应结构为 {@code { total, rows }}。</p>
 */
@Data
public class GbParentPlatformPageRequest {

    /** 当前页码（从 1 开始）；默认 1 */
    private Long page = 1L;

    /** 每页条数；默认 20 */
    private Long pageSize = 20L;

    /**
     * 上级平台名称（模糊匹配，可选）
     */
    private String name;

    /**
     * 上级平台国标编码（模糊匹配，可选）
     */
    private String serverGbId;

    /**
     * 启用状态（可选）：{@code 0} 禁用、{@code 1} 启用
     */
    private Integer enabled;
}
