package com.solo.system.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户信息 VO
 *
 * <p>对应接口 {@code GET /api/auth/profile}，也作为 {@link LoginResponse#user} 内嵌字段。
 * 含"是否首次登录强制改密"标记，前端据此决定是否弹"修改默认密码"提示框。</p>
 */
@Schema(description = "用户信息 VO")
@Data
public class UserInfoVO {

    /** 用户 ID */
    @Schema(description = "用户 ID")
    private Long id;

    /** 登录用户名 */
    @Schema(description = "登录用户名")
    private String username;

    /** 用户昵称 */
    @Schema(description = "用户昵称")
    private String nickname;

    /** 邮箱地址（可空） */
    @Schema(description = "邮箱地址")
    private String email;

    /** 手机号码（可空） */
    @Schema(description = "手机号码")
    private String phone;

    /** 头像 URL（可空） */
    @Schema(description = "头像 URL")
    private String avatar;

    /** 用户状态：{@code 0=禁用}、{@code 1=启用} */
    @Schema(description = "状态（0/1）")
    private Integer status;

    /** 是否首次登录强制改密：{@code 0=否}、{@code 1=是} */
    @Schema(description = "是否首次登录强制改密（0/1）")
    private Integer pwdResetRequired;
}
