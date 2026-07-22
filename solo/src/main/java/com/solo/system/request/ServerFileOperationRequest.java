package com.solo.system.request;

import lombok.Data;

/**
 * 服务器文件操作请求
 */
@Data
public class ServerFileOperationRequest {
    private Long serverId;
    private String path;
}
