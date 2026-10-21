package com.solo.system.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户状态统计")
@Data
public class UserStatusCountVO {

    @Schema(description = "状态（0/1）")
    private Integer status;

    @Schema(description = "用户数")
    private Long count;
}
