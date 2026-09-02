package com.solo.system.request;

import lombok.Data;

/**
 * 角色分页查询请求参数
 *
 * <p>分页参数：{@code page} 从 1 开始，{@code pageSize} 默认 20。
 * 对应接口 {@code POST /api/role/page}。</p>
 */
@Data
public class RolePageRequest {

    /** 当前页码（从 1 开始），缺省 1 */
    private Long page = 1L;

    /** 每页条数，缺省 20 */
    private Long pageSize = 20L;

    /** 角色编码（可选，精确匹配） */
    private String roleCode;

    /** 角色名称（可选，精确匹配） */
    private String roleName;
}
