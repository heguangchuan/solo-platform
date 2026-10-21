package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "用户授权（分配角色）请求")
@Data
public class UserRoleAssignRequest {

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "角色 ID 列表")
    private List<Long> roleIds;
}
