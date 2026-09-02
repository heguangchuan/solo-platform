package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 上级平台推送目录：通道分页查询请求
 *
 * <p>对应 {@code POST /api/gb28181/parent-platform/{id}/share-group/{groupId}/unmounted-channels/page}，
 * 也复用于"已挂载"分页接口。</p>
 */
@Schema(description = "上级平台推送目录通道分页查询请求")
@Data
public class UnmountedChannelPageRequest {

    @Schema(description = "当前页码（从 1 开始）")
    private Long page = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    @Schema(description = "名称 / 通道国标编码 模糊匹配")
    private String keyword;
}
