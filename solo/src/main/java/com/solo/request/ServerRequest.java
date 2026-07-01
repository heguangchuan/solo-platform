package com.solo.request;

import lombok.Data;

/**
 * 服务器新增/编辑请求
 */
@Data
public class ServerRequest {
    private String serverName;
    private String host;
    private Integer port;
    private String username;
    private String authType;
    private String password;
    private String privateKey;
    private String osType;
    private Integer status;
    private String description;
}
