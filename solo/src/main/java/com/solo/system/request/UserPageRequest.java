package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "用户分页查询请求参数")
@Data
public class UserPageRequest {

    @Schema(description = "当前页码")
    @Min(1)
    private Long page = 1L;

    @Schema(description = "每页条数")
    @Min(1)
    @Max(200)
    private Long pageSize = 10L;

    @Schema(description = "登录账号")
    @Size(max = 50)
    private String username;

    @Schema(description = "用户昵称")
    @Size(max = 50)
    private String nickname;

    @Schema(description = "状态（0/1）")
    @Min(0)
    @Max(1)
    private Integer status;
}
