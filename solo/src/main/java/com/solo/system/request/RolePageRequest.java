package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 角色分页查询请求参数
 *
 * <p>分页参数：{@code page} 从 1 开始，{@code pageSize} 默认 20。
 * 对应接口 {@code POST /api/role/page}。</p>
 */
@Schema(description = "角色分页查询请求参数")
@Data
public class RolePageRequest {

    /** 当前页码（从 1 开始），缺省 1 */
    @Schema(description = "当前页码")
    private Long page = 1L;

    /** 每页条数，缺省 20 */
    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    /** 角色编码（可选，精确匹配） */
    @Schema(description = "角色编码")
    private String roleCode;

    /** 角色名称（可选，精确匹配） */
    @Schema(description = "角色名称")
    private String roleName;
}
