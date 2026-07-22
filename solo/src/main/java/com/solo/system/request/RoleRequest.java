package com.solo.system.request;

import lombok.Data;

/**
 * 角色新增/编辑请求体
 * 包含角色基本信息字段
 */
@Data
public class RoleRequest {

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 角色状态：0-禁用，1-启用
     */
    private Integer status;
}
