package com.solo.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 系统角色实体类
 * 对应数据库表 sys_role，存储系统角色的定义信息
 */
@Schema(description = "角色")
@Data
@TableName("sys_role")
public class SysRole {

    /**
     * 角色ID，主键，自增
     */
    @Schema(description = "角色 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 角色编码，唯一标识，如 ADMIN、USER
     */
    @Schema(description = "角色编码")
    private String roleCode;

    /**
     * 角色名称，展示用
     */
    @Schema(description = "角色名称")
    private String roleName;

    /**
     * 角色描述信息
     */
    @Schema(description = "角色描述")
    private String description;

    /**
     * 角色状态：0-禁用，1-启用
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * 更新时间，插入和修改时自动填充
     */
    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime updateTime;
}
