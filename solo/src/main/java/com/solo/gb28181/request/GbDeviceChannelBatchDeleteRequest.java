package com.solo.gb28181.request;

import lombok.Data;

import java.util.List;

/**
 * 批量删除通道请求
 *
 * <p>对应 {@code POST /api/gb28181/device-channel/batch/delete}。</p>
 */
@Data
public class GbDeviceChannelBatchDeleteRequest {

    /**
     * 要删除的通道主键 ID 列表（数据库自增 ID，非国标 channelId）
     */
    private List<Long> ids;
}
