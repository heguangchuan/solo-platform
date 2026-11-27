package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "用户登录请求")
@Data
public class LoginRequest {

    @Schema(description = "登录账号")
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50)
    private String username;

    @Schema(description = "登录密码")
    @NotBlank(message = "密码不能为空")
    @Size(max = 128)
    private String password;
}
