package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户登录请求体")
@Data
public class LoginRequest {

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "登录密码")
    private String password;
}
