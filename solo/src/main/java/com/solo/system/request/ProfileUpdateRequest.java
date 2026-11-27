package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "当前登录用户更新自身资料")
@Data
public class ProfileUpdateRequest {

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

    @Schema(description = "头像URL")
    @Size(max = 255)
    private String avatar;
}
