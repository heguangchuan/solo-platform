package com.solo.gb28181.request;

import lombok.Data;

import java.util.List;

/**
 * 批量删除上级平台请求
 *
 * <p>对应 {@code POST /api/gb28181/parent-platform/batch/delete}。</p>
 */
@Data
public class GbParentPlatformBatchDeleteRequest {

    /**
     * 要删除的上级平台主键 ID 列表（数据库自增 ID）
     */
    private List<Long> ids;
}
