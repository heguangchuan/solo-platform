package com.solo.system.request;

import lombok.Data;

@Data
public class TaskLogPageRequest {
    private Long page;
    private Long pageSize;
    private Long taskId;
    private String taskName;
    private String taskCode;
    private Integer resultStatus;
}
