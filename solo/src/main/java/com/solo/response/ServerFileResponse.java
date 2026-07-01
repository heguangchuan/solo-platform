package com.solo.response;

import lombok.Data;

/**
 * 服务器文件响应
 */
@Data
public class ServerFileResponse {
    private String name;
    private String path;
    private Boolean directory;
    private Long size;
    private String modifiedTime;
}
