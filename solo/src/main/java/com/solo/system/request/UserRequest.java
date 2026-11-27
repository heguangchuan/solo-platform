package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "用户新增/编辑请求体")
@Data
public class UserRequest {

    @Schema(description = "登录用户名，新增时必填；编辑时不允许修改")
    @Size(max = 50)
    private String username;

    @Schema(description = "初始密码，仅新增时使用；首次登录必须修改")
    private String initialPassword;

    @Schema(description = "用户昵称")
    @Size(max = 50)
    private String nickname;

    @Schema(description = "邮箱地址")
    @Email
    @Size(max = 100)
    private String email;

    @Schema(description = "手机号码")
    @Size(max = 20)
    private String phone;

    @Schema(description = "头像 URL")
    @Size(max = 255)
    private String avatar;

    @Schema(description = "状态（0/1）")
    @Min(0)
    @Max(1)
    private Integer status;
}
