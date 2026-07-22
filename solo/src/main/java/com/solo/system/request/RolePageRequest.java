package com.solo.system.request;

import lombok.Data;

/**
 * 角色分页查询请求参数
 */
@Data
public class RolePageRequest {
    private Long page = 1L;
    private Long pageSize = 20L;
    private String roleCode;
    private String roleName;
}
