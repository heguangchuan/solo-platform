package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 批量检测设备存活请求
 *
 * <p>对应 {@code POST /api/gb28181/device/detect}。探测结果异步更新，
 * 响应只汇报发起 / 跳过的台数，前端提示后自行刷新列表查看最新状态。</p>
 */
@Schema(description = "批量检测设备存活请求")
@Data
public class GbDeviceDetectRequest {

    @Schema(description = "要检测的设备主键 ID 列表")
    private List<Long> ids;
}
