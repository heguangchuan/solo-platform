package com.solo.media.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 推流列表分页查询
 *
 * <p>对应 {@code POST /api/stream-push/page}。
 * 推流列表页直接绑定该 DTO；后端按所传条件过滤后返回 {@code { total, rows }} 形式的分页结果。</p>
 */
@Schema(description = "推流列表分页查询")
@Data
public class StreamPushPageRequest {

    @Schema(description = "页码（从 1 开始；默认 1）")
    private Long page = 1L;

    @Schema(description = "每页条数（默认 20）")
    private Long pageSize = 20L;

    @Schema(description = "ZLM app 名（如 live）")
    private String app;

    @Schema(description = "ZLM stream 名")
    private String stream;

    @Schema(description = "流媒体节点 serverId 精确过滤（可选）")
    private String mediaServerId;
}
