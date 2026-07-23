package com.solo.media.request;

import lombok.Data;

import java.util.List;

/**
 * 拉流代理批量删除请求
 */
@Data
public class StreamProxyBatchDeleteRequest {

    private List<Long> ids;
}
