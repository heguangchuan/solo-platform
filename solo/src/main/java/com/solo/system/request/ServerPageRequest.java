package com.solo.system.request;

import lombok.Data;

/**
 * 服务器分页查询请求
 */
@Data
public class ServerPageRequest {
    private Long page = 1L;
    private Long pageSize = 20L;
    private String serverName;
    private String host;
    private Integer status;
}
