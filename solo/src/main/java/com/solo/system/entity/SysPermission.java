package com.solo.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统权限实体类
 * 对应数据库表 sys_permission，存储菜单、按钮权限的定义
 */
@Schema(description = "权限")
@Data
@TableName("sys_permission")
public class SysPermission {

    /**
     * 权限ID，主键，自增
     */
    @Schema(description = "权限 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 权限名称，如"用户管理"、"用户新增"
     */
    @Schema(description = "权限名称")
    private String permissionName;

    /**
     * 权限编码，唯一标识，如 system:user:add
     */
    @Schema(description = "权限编码")
    private String permissionCode;

    /**
     * 关联的前端URL路径
     */
    @Schema(description = "前端 URL 路径")
    private String url;

    /**
     * 权限类型：1-菜单，2-按钮
     */
    @Schema(description = "权限类型（1=菜单，2=按钮）")
    private Integer type;

    /**
     * 父级权限ID，0表示顶级权限
     */
    @Schema(description = "父 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    /**
     * 排序号，数字越小越靠前
     */
    @Schema(description = "排序（升序）")
    private Integer sort;

    /**
     * 图标标识
     */
    @Schema(description = "图标")
    private String icon;

    /**
     * 权限状态：0-禁用，1-启用
     */
    @Schema(description = "状态（0=停用，1=启用）")
    private Integer status;

    /**
     * 逻辑删除标识：0-未删除，1-已删除
     */
    @Schema(description = "逻辑删除（0=未删，1=已删）")
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间，插入时自动填充
     */
    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间，插入和修改时自动填充
     */
    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime updateTime;

    /**
     * 子权限列表（非数据库字段，用于前端树形渲染）
     */
    @Schema(description = "子权限列表")
    @TableField(exist = false)
    private List<SysPermission> children;
}
