package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "用户新增/编辑请求体")
@Data
public class UserRequest {

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

    @Schema(description = "角色ID列表（新增时必填，至少一个）")
    private List<Long> roleIds;
}
