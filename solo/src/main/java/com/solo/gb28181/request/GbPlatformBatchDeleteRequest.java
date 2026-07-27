package com.solo.gb28181.request;

import lombok.Data;

import java.util.List;

/**
 * 批量删除下级级联平台请求
 */
@Data
public class GbPlatformBatchDeleteRequest {

    /**
     * 要删除的平台主键 ID 列表
     */
    private List<Long> ids;
}