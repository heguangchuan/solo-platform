package com.solo.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 系统用户实体类
 * 对应数据库表 sys_user，存储系统用户的基本信息
 */
@Schema(description = "用户")
@Data
@TableName("sys_user")
public class SysUser {

    /**
     * 用户ID，主键，自增
     */
    @Schema(description = "用户 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 登录用户名，唯一，不可修改
     */
    @Schema(description = "登录账号")
    private String username;

    /**
     * MD5加密后的密码
     */
    @Schema(description = "密码（BCrypt 哈希）")
    private String password;

    /**
     * 用户昵称，展示用
     */
    @Schema(description = "昵称")
    private String nickname;

    /**
     * 用户邮箱地址
     */
    @Schema(description = "邮箱")
    private String email;

    /**
     * 用户手机号码
     */
    @Schema(description = "手机号")
    private String phone;

    /**
     * 头像URL地址
     */
    @Schema(description = "头像 URL")
    private String avatar;

    /**
     * 用户状态：0-禁用，1-启用
     */
    @Schema(description = "状态（0=停用，1=启用）")
    private Integer status;

    /**
     * 是否首次登录强制改密：0-否，1-是
     */
    @Schema(description = "是否首次登录强制改密（0=否，1=是）")
    private Integer pwdResetRequired;

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
