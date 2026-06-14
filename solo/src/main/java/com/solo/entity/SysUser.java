package com.solo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体类
 * 对应数据库表 sys_user，存储系统用户的基本信息
 */
@Data
@TableName("sys_user")
public class SysUser {

    /**
     * 用户ID，主键，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 登录用户名，唯一，不可修改
     */
    private String username;

    /**
     * MD5加密后的密码
     */
    private String password;

    /**
     * 用户昵称，展示用
     */
    private String nickname;

    /**
     * 用户邮箱地址
     */
    private String email;

    /**
     * 用户手机号码
     */
    private String phone;

    /**
     * 头像URL地址
     */
    private String avatar;

    /**
     * 用户状态：0-禁用，1-启用
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
     * 创建人ID
     */
    private Long createBy;
}
