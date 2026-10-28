package com.solo.openapi.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "开放API调用日志分页查询请求")
@Data
public class OpenApiLogPageRequest {

    @Schema(description = "页码，从1开始")
    private Long page = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    @Schema(description = "应用 appKey，模糊匹配")
    private String appKey;

    @Schema(description = "请求路径，模糊匹配")
    private String path;

    @Schema(description = "调用结果：true 成功（业务码200）/ false 失败")
    private Boolean success;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "开始时间", example = "2026-10-28 00:00:00")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "结束时间", example = "2026-10-28 23:59:59")
    private LocalDateTime endTime;
}
