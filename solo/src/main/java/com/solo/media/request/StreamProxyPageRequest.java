package com.solo.media.request;

import lombok.Data;

/**
 * 拉流代理分页查询请求
 *
 * <p>对应 {@code POST /api/stream-proxy/page}。
 * 拉流代理列表页直接绑定该 DTO；后端按所传条件过滤后返回 {@code { total, rows }} 形式的分页结果。</p>
 */
@Data
public class StreamProxyPageRequest {

    /** 页码（从 1 开始；默认 1） */
    private Long page = 1L;

    /** 每页条数（默认 20） */
    private Long pageSize = 20L;

    /** app 精确匹配（可选；如 {@code proxy}） */
    private String app;

    /** stream 模糊匹配（可选） */
    private String stream;

    /** 运行状态精确过滤（可选）：0 未启动，1 已启动 */
    private Integer status;
}
