package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "角色授权（分配权限）请求")
@Data
public class RolePermissionAssignRequest {

    @Schema(description = "角色 ID")
    private Long roleId;

    @Schema(description = "权限 ID 列表")
    private List<Long> permissionIds;
}
