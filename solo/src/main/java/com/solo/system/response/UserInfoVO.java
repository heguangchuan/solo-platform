package com.solo.system.response;

import lombok.Data;

/**
 * 用户信息 VO
 *
 * <p>对应接口 {@code GET /api/auth/profile}，也作为 {@link LoginResponse#user} 内嵌字段。
 * 含"是否首次登录强制改密"标记，前端据此决定是否弹"修改默认密码"提示框。</p>
 */
@Data
public class UserInfoVO {

    /** 用户 ID */
    private Long id;

    /** 登录用户名 */
    private String username;

    /** 用户昵称 */
    private String nickname;

    /** 邮箱地址（可空） */
    private String email;

    /** 手机号码（可空） */
    private String phone;

    /** 头像 URL（可空） */
    private String avatar;

    /** 用户状态：{@code 0=禁用}、{@code 1=启用} */
    private Integer status;

    /** 是否首次登录强制改密：{@code 0=否}、{@code 1=是} */
    private Integer pwdResetRequired;
}
