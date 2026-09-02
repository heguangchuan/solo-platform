package com.solo.system.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 用户认证响应体（菜单树节点）
 *
 * <p>对应接口 {@code GET /api/auth/me}，已按 {@code parentId} 嵌套成树（{@code children} 字段递归）。
 * 仅含前端菜单 / 路由渲染所需的最少字段，权限码（按钮级）见 {@code /api/auth/permissions}。</p>
 */
@Schema(description = "用户认证响应体（菜单树节点）")
@Data
public class UserAuthResponse {

    /** 节点 ID（字符串形式，与 {@code /api/permission/tree} 一致） */
    @Schema(description = "节点 ID")
    private Long id;

    /** 菜单名称（前端展示） */
    @Schema(description = "菜单名称")
    private String permissionName;

    /** 图标标识（前端菜单 icon 用） */
    @Schema(description = "图标标识")
    private String icon;

    /** 父级 ID（{@code 0} 表示顶级） */
    @Schema(description = "父级 ID")
    private Long parentId;

    /** 排序号（升序） */
    @Schema(description = "排序号")
    private Integer sort;

    /** 关联的 URL 路径（前端路由用） */
    @Schema(description = "URL 路径")
    private String url;

    /** 子节点（递归结构；前端按 children 递归渲染） */
    @Schema(description = "子节点")
    private List<UserAuthResponse> children;
}
