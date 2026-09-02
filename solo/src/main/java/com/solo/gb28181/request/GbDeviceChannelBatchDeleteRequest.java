package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 批量删除通道请求
 *
 * <p>对应 {@code POST /api/gb28181/device-channel/batch/delete}。</p>
 */
@Schema(description = "批量删除通道请求")
@Data
public class GbDeviceChannelBatchDeleteRequest {

    @Schema(description = "要删除的通道主键 ID 列表")
    private List<Long> ids;
}
