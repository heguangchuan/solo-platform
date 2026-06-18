package com.solo.request;

import lombok.Data;

@Data
public class TaskRequest {
    private String taskName;
    private String taskCode;
    private String cron;
    private String handlerName;
    private String taskParams;
    private Integer status;
    private String remark;
}
