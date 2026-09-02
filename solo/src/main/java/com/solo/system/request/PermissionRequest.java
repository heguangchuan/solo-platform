package com.solo.system.request;

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
@Data
public class PermissionRequest {

    /** 权限名称（必填） */
    private String permissionName;

    /** 权限编码（必填，全局唯一；与 {@code v-permission} 指令对应） */
    private String permissionCode;

    /** 关联的 URL 路径（菜单必填，按钮可空） */
    private String url;

    /** 权限类型（必填）：{@code 1=菜单}、{@code 2=按钮} */
    private Integer type;

    /** 父级权限 ID（必填，{@code 0} 表示顶级） */
    private Long parentId;

    /** 排序号（可选，缺省 0；升序） */
    private Integer sort;

    /** 图标标识（可选，前端菜单 icon 用） */
    private String icon;

    /** 权限状态（可选）：{@code 0=禁用}、{@code 1=启用} */
    private Integer status;
}
