package com.solo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统角色实体类
 * 对应数据库表 sys_role，存储系统角色的定义信息
 */
@Data
@TableName("sys_role")
public class SysRole {

    /**
     * 角色ID，主键，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 角色编码，唯一标识，如 ADMIN、USER
     */
    private String roleCode;

    /**
     * 角色名称，展示用
     */
    private String roleName;

    /**
     * 角色描述信息
     */
    private String description;

    /**
     * 角色状态：0-禁用，1-启用
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
}
