package com.solo.media.request;

import lombok.Data;

import java.util.List;

/**
 * 拉流代理批量删除请求
 *
 * <p>对应 {@code POST /api/stream-proxy/batch/delete}。
 * 一次性删除多条拉流代理；删除时会先停流再删行，过程中单条失败不影响其它。</p>
 */
@Data
public class StreamProxyBatchDeleteRequest {

    /** 代理主键 ID 列表（必填；可任意长度） */
    private List<Long> ids;
}
