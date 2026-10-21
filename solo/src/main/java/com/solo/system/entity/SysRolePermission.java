package com.solo.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Schema(description = "角色权限关联")
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "角色 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roleId;

    @Schema(description = "权限 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long permissionId;

}
