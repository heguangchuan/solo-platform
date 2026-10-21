package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "批量检测设备存活请求")
@Data
public class GbDeviceDetectRequest {

    @Schema(description = "要检测的设备主键 ID 列表")
    private List<Long> ids;
}
