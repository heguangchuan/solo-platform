package com.solo.request;

import lombok.Data;

@Data
public class TaskPageRequest {
    private Long page;
    private Long pageSize;
    private String taskName;
    private String taskCode;
    private Integer status;
}
