package com.solo.system.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "系统统计信息响应体")
@Data
public class StatResponse {

    @Schema(description = "系统用户总数")
    private Long userCount;

    @Schema(description = "系统角色总数")
    private Long roleCount;

    @Schema(description = "系统权限总数")
    private Long permissionCount;
}
