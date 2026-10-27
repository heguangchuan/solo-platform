package com.solo.openapi.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "开放API应用分页查询请求")
@Data
public class OpenAppPageRequest {

    @Schema(description = "页码，从1开始")
    private Long page = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 10L;

    @Schema(description = "应用名称，模糊匹配")
    private String appName;

    @Schema(description = "状态 0停用 1启用")
    private Integer status;
}
