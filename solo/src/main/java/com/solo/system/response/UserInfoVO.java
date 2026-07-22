package com.solo.system.response;

import lombok.Data;

/**
 * 用户信息VO
 * 用于前端展示用户基本信息
 */
@Data
public class UserInfoVO {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 登录用户名
     */
    private String username;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 邮箱地址
     */
    private String email;

    /**
     * 手机号码
     */
    private String phone;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 用户状态：0-禁用，1-启用
     */
    private Integer status;

    /**
     * 是否首次登录强制改密：0-否，1-是
     */
    private Integer pwdResetRequired;
}
