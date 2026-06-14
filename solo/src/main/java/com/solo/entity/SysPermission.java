package com.solo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统权限实体类
 * 对应数据库表 sys_permission，存储菜单、按钮、接口权限的定义
 */
@Data
@TableName("sys_permission")
public class SysPermission {

    /**
     * 权限ID，主键，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 权限名称，如"用户管理"、"用户新增"
     */
    private String permissionName;

    /**
     * 权限编码，唯一标识，如 system:user:add
     */
    private String permissionCode;

    /**
     * 关联的前端URL路径
     */
    private String url;

    /**
     * 权限类型：1-菜单，2-按钮，3-接口
     */
    private Integer type;

    /**
     * 父级权限ID，0表示顶级权限
     */
    private Long parentId;

    /**
     * 排序号，数字越小越靠前
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

    /**
     * 创建时间，插入时自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间，插入和修改时自动填充
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 子权限列表（非数据库字段，用于前端树形渲染）
     */
    @TableField(exist = false)
    private List<SysPermission> children;
}
