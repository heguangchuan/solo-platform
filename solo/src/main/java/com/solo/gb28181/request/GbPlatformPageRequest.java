package com.solo.gb28181.request;

import lombok.Data;

/**
 * 下级级联平台分页查询请求
 *
 * <p>对应 {@code POST /api/gb28181/platform/page}（黑名单列表页）。分页参数：{@code page} 从 1 开始，默认 1；{@code pageSize} 默认 20。
 * 响应结构为 {@code { total, rows }}。</p>
 */
@Data
public class GbPlatformPageRequest {

    /** 当前页码（从 1 开始）；默认 1 */
    private Long page = 1L;

    /** 每页条数；默认 20 */
    private Long pageSize = 20L;

    /**
     * 平台国标 ID（模糊匹配，可选）
     */
    private String platformId;

    /**
     * 平台名称（模糊匹配，可选）
     */
    private String name;

    /**
     * 启用状态（可选）：{@code 0} 禁用、{@code 1} 启用
     */
    private Integer enabled;
}