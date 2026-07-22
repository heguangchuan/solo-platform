package com.solo.system.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志分页查询请求
 */
@Data
public class LogPageRequest {
    private Long page = 1L;
    private Long pageSize = 20L;
    private String operatorName;
    private String module;
    private String businessType;
    private Integer responseStatus;
    private String clientSource;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;
}
