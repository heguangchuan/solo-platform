package com.solo.media.request;

import lombok.Data;

/**
 * 推流列表分页查询
 */
@Data
public class StreamPushPageRequest {

    private Long page = 1L;
    private Long pageSize = 20L;

    /** app 精确 */
    private String app;

    /** stream 模糊 */
    private String stream;

    /** 节点 serverId 精确 */
    private String mediaServerId;
}
