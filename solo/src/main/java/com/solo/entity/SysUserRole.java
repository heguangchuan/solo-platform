package com.solo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

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
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 用户ID，外键关联 sys_user.id
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /**
     * 角色ID，外键关联 sys_role.id
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roleId;
}
