package com.solo.gb28181.request;

import lombok.Data;

/**
 * 上级平台推送目录：未挂载通道分页查询请求
 */
@Data
public class UnmountedChannelPageRequest {

    private Long page = 1L;
    private Long pageSize = 20L;

    /**
     * 名称 / 通道国标编码 模糊匹配（任一命中即返回）
     */
    private String keyword;
}
