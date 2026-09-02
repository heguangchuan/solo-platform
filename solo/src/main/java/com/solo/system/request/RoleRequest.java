package com.solo.system.request;

import lombok.Data;

/**
 * 角色新增/编辑请求体
 *
 * <p>对应接口 {@code POST /api/role}、{@code PUT /api/role/{id}}。</p>
 */
@Data
public class RoleRequest {

    /** 角色编码（必填，全局唯一） */
    private String roleCode;

    /** 角色名称（必填） */
    private String roleName;

    /** 角色描述（可选） */
    private String description;

    /** 角色状态（可选）：{@code 0=禁用}、{@code 1=启用} */
    private Integer status;
}
