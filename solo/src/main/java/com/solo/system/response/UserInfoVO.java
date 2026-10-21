package com.solo.system.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户信息 VO")
@Data
public class UserInfoVO {

    @Schema(description = "用户 ID")
    private Long id;

    @Schema(description = "登录用户名")
    private String username;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "邮箱地址")
    private String email;

    @Schema(description = "手机号码")
    private String phone;

    @Schema(description = "头像 URL")
    private String avatar;

    @Schema(description = "状态（0/1）")
    private Integer status;

    @Schema(description = "是否首次登录强制改密（0/1）")
    private Integer pwdResetRequired;
}
