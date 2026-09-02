package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 权限新增/编辑请求体
 *
 * <p>对应接口 {@code POST /api/permission}、{@code PUT /api/permission/{id}}。
 * {@code permissionCode} 全局唯一，新增后给 {@code v-permission} 指令使用。</p>
 *
 * <h3>字段取值</h3>
 * <ul>
 *   <li>{@code type} — {@code 1=菜单}、{@code 2=按钮}</li>
 *   <li>{@code status} — {@code 0=禁用}、{@code 1=启用}</li>
 *   <li>{@code parentId} — {@code 0} 表示顶级</li>
 * </ul>
 */
@Schema(description = "权限新增/编辑请求体")
@Data
public class PermissionRequest {

    /** 权限名称（必填） */
    @Schema(description = "权限名称")
    private String permissionName;

    /** 权限编码（必填，全局唯一；与 {@code v-permission} 指令对应） */
    @Schema(description = "权限编码")
    private String permissionCode;

    /** 关联的 URL 路径（菜单必填，按钮可空） */
    @Schema(description = "URL 路径")
    private String url;

    /** 权限类型（必填）：{@code 1=菜单}、{@code 2=按钮} */
    @Schema(description = "权限类型（1=菜单、2=按钮）")
    private Integer type;

    /** 父级权限 ID（必填，{@code 0} 表示顶级） */
    @Schema(description = "父级权限 ID")
    private Long parentId;

    /** 排序号（可选，缺省 0；升序） */
    @Schema(description = "排序号")
    private Integer sort;

    /** 图标标识（可选，前端菜单 icon 用） */
    @Schema(description = "图标标识")
    private String icon;

    /** 权限状态（可选）：{@code 0=禁用}、{@code 1=启用} */
    @Schema(description = "状态（0/1）")
    private Integer status;
}
