package com.solo.media.request;

import lombok.Data;

/**
 * 拉流代理分页查询请求
 */
@Data
public class StreamProxyPageRequest {

    private Long page = 1L;
    private Long pageSize = 20L;

    /** app 精确 */
    private String app;

    /** stream 模糊 */
    private String stream;

    /** 运行状态 0未启动 1已启动 */
    private Integer status;
}
