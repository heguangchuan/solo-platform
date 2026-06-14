package com.solo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色权限关联实体类
 * 对应数据库表 sys_role_permission，存储角色与权限的多对多关系
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    /**
     * 关联ID，主键，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 角色ID，外键关联 sys_role.id
     */
    private Long roleId;

    /**
     * 权限ID，外键关联 sys_permission.id
     */
    private Long permissionId;

    /**
     * 创建时间，插入时自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
