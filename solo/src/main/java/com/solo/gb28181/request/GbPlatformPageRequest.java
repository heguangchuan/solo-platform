package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "下级级联平台分页查询请求")
@Data
public class GbPlatformPageRequest {

    @Schema(description = "当前页码（从 1 开始）")
    private Long page = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    @Schema(description = "平台国标 ID（模糊匹配）")
    private String platformId;

    @Schema(description = "平台名称（模糊匹配）")
    private String name;

    @Schema(description = "启用状态：0 禁用、1 启用")
    private Integer enabled;
}