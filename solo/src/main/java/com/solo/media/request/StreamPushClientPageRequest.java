package com.solo.media.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "推流方分页查询请求")
@Data
public class StreamPushClientPageRequest {

    @Schema(description = "页码（从 1 开始；默认 1）")
    private Long page = 1L;

    @Schema(description = "每页条数（默认 20）")
    private Long pageSize = 20L;

    @Schema(description = "推流方账号")
    private String clientId;

    @Schema(description = "启用状态（0/1）")
    private Integer enabled;
}
