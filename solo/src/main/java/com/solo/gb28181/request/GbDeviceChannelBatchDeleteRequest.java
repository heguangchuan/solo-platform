package com.solo.gb28181.request;

import lombok.Data;

import java.util.List;

/**
 * 批量删除通道请求
 */
@Data
public class GbDeviceChannelBatchDeleteRequest {

    /**
     * 要删除的通道ID列表
     */
    private List<Long> ids;
}
