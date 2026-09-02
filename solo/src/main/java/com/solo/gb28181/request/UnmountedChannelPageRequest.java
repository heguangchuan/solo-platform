package com.solo.gb28181.request;

import lombok.Data;

/**
 * 上级平台推送目录：通道分页查询请求
 *
 * <p>对应 {@code POST /api/gb28181/parent-platform/{id}/share-group/{groupId}/unmounted-channels/page}，
 * 也复用于"已挂载"分页接口。</p>
 */
@Data
public class UnmountedChannelPageRequest {

    /** 当前页码（从 1 开始）；默认 1 */
    private Long page = 1L;

    /** 每页条数；默认 20 */
    private Long pageSize = 20L;

    /**
     * 名称 / 通道国标编码 模糊匹配（任一命中即返回，可选）
     */
    private String keyword;
}
