package com.solo.gb28181.request;

import lombok.Data;

import java.util.List;

/**
 * 批量删除设备请求
 */
@Data
public class GbDeviceBatchDeleteRequest {

    /**
     * 要删除的设备ID列表
     */
    private List<Long> ids;
}
