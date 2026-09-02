package com.solo.media.request;

import lombok.Data;

/**
 * 推流列表分页查询
 *
 * <p>对应 {@code POST /api/stream-push/page}。
 * 推流列表页直接绑定该 DTO；后端按所传条件过滤后返回 {@code { total, rows }} 形式的分页结果。</p>
 */
@Data
public class StreamPushPageRequest {

    /** 页码（从 1 开始；默认 1） */
    private Long page = 1L;

    /** 每页条数（默认 20） */
    private Long pageSize = 20L;

    /** app 精确匹配（可选；如 {@code live}） */
    private String app;

    /** stream 模糊匹配（可选） */
    private String stream;

    /** 流媒体节点 serverId 精确过滤（可选） */
    private String mediaServerId;
}
