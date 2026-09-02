package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 角色新增/编辑请求体
 *
 * <p>对应接口 {@code POST /api/role}、{@code PUT /api/role/{id}}。</p>
 */
@Schema(description = "角色新增/编辑请求体")
@Data
public class RoleRequest {

    /** 角色编码（必填，全局唯一） */
    @Schema(description = "角色编码")
    private String roleCode;

    /** 角色名称（必填） */
    @Schema(description = "角色名称")
    private String roleName;

    /** 角色描述（可选） */
    @Schema(description = "角色描述")
    private String description;

    /** 角色状态（可选）：{@code 0=禁用}、{@code 1=启用} */
    @Schema(description = "状态（0/1）")
    private Integer status;
}
