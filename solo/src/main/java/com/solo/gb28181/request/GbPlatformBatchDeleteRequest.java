package com.solo.gb28181.request;

import lombok.Data;

import java.util.List;

/**
 * 批量删除下级级联平台请求
 *
 * <p>对应 {@code POST /api/gb28181/platform/batch/delete}。
 * 单条走 {@code DELETE /{id}}，复用同一服务方法以确保拦截名单缓存立即失效。</p>
 */
@Data
public class GbPlatformBatchDeleteRequest {

    /**
     * 要删除的平台主键 ID 列表（数据库自增 ID）
     */
    private List<Long> ids;
}