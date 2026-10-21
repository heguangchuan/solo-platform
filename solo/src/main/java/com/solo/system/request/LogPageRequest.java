package com.solo.system.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "操作日志分页查询请求")
@Data
public class LogPageRequest {

    @Schema(description = "当前页码")
    private Long page = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    @Schema(description = "操作人账号")
    private String operatorName;

    @Schema(description = "业务模块")
    private String module;

    @Schema(description = "业务类型")
    private String businessType;

    @Schema(description = "响应状态（0/1）")
    private Integer responseStatus;

    @Schema(description = "客户来源")
    private String clientSource;

    @Schema(description = "起始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    @Schema(description = "截止时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;
}
