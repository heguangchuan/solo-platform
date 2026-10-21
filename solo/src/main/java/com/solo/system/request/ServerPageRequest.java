package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "服务器分页查询请求")
@Data
public class ServerPageRequest {

    @Schema(description = "当前页码")
    private Long page = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    @Schema(description = "服务器名称")
    private String serverName;

    @Schema(description = "主机地址")
    private String host;

    @Schema(description = "状态（0/1）")
    private Integer status;
}
