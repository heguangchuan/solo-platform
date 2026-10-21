package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "角色分页查询请求参数")
@Data
public class RolePageRequest {

    @Schema(description = "当前页码")
    private Long page = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    @Schema(description = "角色编码")
    private String roleCode;

    @Schema(description = "角色名称")
    private String roleName;
}
