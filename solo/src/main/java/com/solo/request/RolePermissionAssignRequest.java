package com.solo.request;

import lombok.Data;

import java.util.List;

/**
 * 角色授权（分配权限）请求
 */
@Data
public class RolePermissionAssignRequest {

    /**
     * 角色ID
     */
    private Long roleId;

    /**
     * 权限ID列表
     */
    private List<Long> permissionIds;
}
