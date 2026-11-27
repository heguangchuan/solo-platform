package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "修改当前登录用户密码请求体")
@Data
public class ChangePasswordRequest {

    @Schema(description = "旧密码")
    @NotBlank(message = "原密码不能为空")
    @Size(max = 128)
    private String oldPassword;

    @Schema(description = "新密码")
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 32)
    private String newPassword;
}
