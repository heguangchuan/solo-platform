package com.solo.system.request;

import lombok.Data;

import java.util.List;

/**
 * 角色授权（分配权限）请求
 *
 * <p>对应接口 {@code POST /api/role/p2r}，全量替换该角色已有权限集：
 * 传入的 {@code permissionIds} 即为最新全量，不在其中的会被取消。</p>
 */
@Data
public class RolePermissionAssignRequest {

    /** 角色 ID（必填，主键） */
    private Long roleId;

    /** 权限 ID 列表（必填；空数组表示清空） */
    private List<Long> permissionIds;
}
