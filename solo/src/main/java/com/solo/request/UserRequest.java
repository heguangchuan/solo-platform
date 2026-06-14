package com.solo.request;

import lombok.Data;

/**
 * 用户新增/编辑请求体
 * 包含用户基本信息字段
 */
@Data
public class UserRequest {

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
}
