package com.solo.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 角色权限关联实体类
 * 对应数据库表 sys_role_permission，存储角色与权限的多对多关系
 */
@Schema(description = "角色权限关联")
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    /**
     * 关联ID，主键，自增
     */
    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 角色ID，外键关联 sys_role.id
     */
    @Schema(description = "角色 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roleId;

    /**
     * 权限ID，外键关联 sys_permission.id
     */
    @Schema(description = "权限 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long permissionId;

}
