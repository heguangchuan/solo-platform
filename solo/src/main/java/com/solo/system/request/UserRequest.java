package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 用户新增/编辑请求体
 *
 * <p>对应接口 {@code POST /api/user}、{@code PUT /api/user/{id}}。
 * <b>不含密码字段</b>——新增用户的密码由后端按 {@code username@123456} 默认值初始化，
 * 改密走 {@code POST /api/auth/password} 或首次登录强制改密流程。</p>
 */
@Schema(description = "用户新增/编辑请求体")
@Data
public class UserRequest {

    /** 登录用户名（必填，全局唯一；编辑时不可修改） */
    @Schema(description = "登录用户名")
    private String username;

    /** 用户昵称（必填） */
    @Schema(description = "用户昵称")
    private String nickname;

    /** 邮箱地址（可选） */
    @Schema(description = "邮箱地址")
    private String email;

    /** 手机号码（可选） */
    @Schema(description = "手机号码")
    private String phone;

    /** 头像 URL（可选） */
    @Schema(description = "头像 URL")
    private String avatar;

    /** 用户状态（可选）：{@code 0=禁用}、{@code 1=启用} */
    @Schema(description = "状态（0/1）")
    private Integer status;

    /** 角色ID列表（仅新增时使用；建议至少分配一个，未分配角色的账号无法登录） */
    @Schema(description = "角色ID列表（仅新增时使用，至少一个）")
    private List<Long> roleIds;
}
