package com.solo.media.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "拉流代理分页查询请求")
@Data
public class StreamProxyPageRequest {

    @Schema(description = "页码（从 1 开始；默认 1）")
    private Long page = 1L;

    @Schema(description = "每页条数（默认 20）")
    private Long pageSize = 20L;

    @Schema(description = "ZLM app 名（如 proxy）")
    private String app;

    @Schema(description = "ZLM stream 名")
    private String stream;

    @Schema(description = "运行状态精确过滤（可选）：0 未启动，1 已启动")
    private Integer status;
}
