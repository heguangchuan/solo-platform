package com.solo.system.request;

import lombok.Data;

/**
 * 权限新增/编辑请求体
 * 包含权限定义的基本字段
 */
@Data
public class PermissionRequest {

    /**
     * 权限名称
     */
    private String permissionName;

    /**
     * 权限编码
     */
    private String permissionCode;

    /**
     * 关联的URL路径
     */
    private String url;

    /**
     * 权限类型：1-菜单，2-按钮
     */
    private Integer type;

    /**
     * 父级权限ID，0表示顶级
     */
    private Long parentId;

    /**
     * 排序号
     */
    private Integer sort;

    /**
     * 图标标识
     */
    private String icon;

    /**
     * 权限状态：0-禁用，1-启用
     */
    private Integer status;
}
