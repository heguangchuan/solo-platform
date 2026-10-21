package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "当前登录用户更新自身资料请求体")
@Data
public class ProfileUpdateRequest {

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "邮箱地址")
    private String email;

    @Schema(description = "手机号码")
    private String phone;

    @Schema(description = "头像URL")
    private String avatar;
}
