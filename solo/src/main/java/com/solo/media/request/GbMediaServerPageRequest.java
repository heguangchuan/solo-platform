package com.solo.media.request;

import lombok.Data;

/**
 * 流媒体节点分页查询请求
 *
 * <p>对应 {@code POST /api/media-server/page}。
 * 列表页直接绑定该 DTO；后端按所传条件过滤后返回 {@code { total, rows }} 形式的分页结果。</p>
 */
@Data
public class GbMediaServerPageRequest {

    /** 页码（从 1 开始；默认 1） */
    private Long page = 1L;

    /** 每页条数（默认 20） */
    private Long pageSize = 20L;

    /** 节点 serverId 模糊匹配（可选） */
    private String serverId;

    /** 在线状态精确过滤（可选）：0 离线，1 在线 */
    private Integer status;
}
