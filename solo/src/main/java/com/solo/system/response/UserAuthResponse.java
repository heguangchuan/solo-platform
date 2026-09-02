package com.solo.system.response;

import lombok.Data;

import java.util.List;

/**
 * 用户认证响应体（菜单树节点）
 *
 * <p>对应接口 {@code GET /api/auth/me}，已按 {@code parentId} 嵌套成树（{@code children} 字段递归）。
 * 仅含前端菜单 / 路由渲染所需的最少字段，权限码（按钮级）见 {@code /api/auth/permissions}。</p>
 */
@Data
public class UserAuthResponse {

    /** 节点 ID（字符串形式，与 {@code /api/permission/tree} 一致） */
    private Long id;

    /** 菜单名称（前端展示） */
    private String permissionName;

    /** 图标标识（前端菜单 icon 用） */
    private String icon;

    /** 父级 ID（{@code 0} 表示顶级） */
    private Long parentId;

    /** 排序号（升序） */
    private Integer sort;

    /** 关联的 URL 路径（前端路由用） */
    private String url;

    /** 子节点（递归结构；前端按 children 递归渲染） */
    private List<UserAuthResponse> children;
}
