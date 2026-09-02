package com.solo.system.request;

import lombok.Data;

/**
 * 当前登录用户更新自身资料请求体
 *
 * <p>仅允许修改昵称 / 邮箱 / 手机号 / 头像，其余字段（账号、状态等）禁止变更。
 * 对应接口 {@code PUT /api/auth/profile}。</p>
 */
@Data
public class ProfileUpdateRequest {

    /** 用户昵称（必填） */
    private String nickname;

    /** 邮箱地址（可选） */
    private String email;

    /** 手机号码（可选） */
    private String phone;

    /** 头像URL（可选） */
    private String avatar;
}
