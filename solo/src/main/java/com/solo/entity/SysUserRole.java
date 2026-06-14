package com.solo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户角色关联实体类
 * 对应数据库表 sys_user_role，存储用户与角色的多对多关系
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    /**
     * 关联ID，主键，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID，外键关联 sys_user.id
     */
    private Long userId;

    /**
     * 角色ID，外键关联 sys_role.id
     */
    private Long roleId;

    /**
     * 创建时间，插入时自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
