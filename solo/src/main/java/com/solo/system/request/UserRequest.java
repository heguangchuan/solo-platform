package com.solo.system.request;

import lombok.Data;

/**
 * 用户新增/编辑请求体
 *
 * <p>对应接口 {@code POST /api/user}、{@code PUT /api/user/{id}}。
 * <b>不含密码字段</b>——新增用户的密码由后端按 {@code username@123456} 默认值初始化，
 * 改密走 {@code POST /api/auth/password} 或首次登录强制改密流程。</p>
 */
@Data
public class UserRequest {

    /** 登录用户名（必填，全局唯一；编辑时不可修改） */
    private String username;

    /** 用户昵称（必填） */
    private String nickname;

    /** 邮箱地址（可选） */
    private String email;

    /** 手机号码（可选） */
    private String phone;

    /** 头像 URL（可选） */
    private String avatar;

    /** 用户状态（可选）：{@code 0=禁用}、{@code 1=启用} */
    private Integer status;
}
