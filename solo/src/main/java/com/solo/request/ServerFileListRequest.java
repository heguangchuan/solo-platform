package com.solo.request;

import lombok.Data;

/**
 * 服务器文件列表请求
 */
@Data
public class ServerFileListRequest {
    private Long serverId;
    private String path;
}
