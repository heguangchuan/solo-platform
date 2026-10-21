package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "上级平台分页查询请求")
@Data
public class GbParentPlatformPageRequest {

    @Schema(description = "当前页码（从 1 开始）")
    private Long page = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    @Schema(description = "上级平台名称（模糊匹配）")
    private String name;

    @Schema(description = "上级平台国标编码（模糊匹配）")
    private String serverGbId;

    @Schema(description = "启用状态：0 禁用、1 启用")
    private Integer enabled;
}
