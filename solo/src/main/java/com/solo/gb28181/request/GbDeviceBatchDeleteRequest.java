package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "批量删除设备请求")
@Data
public class GbDeviceBatchDeleteRequest {

    @Schema(description = "要删除的设备主键 ID 列表")
    private List<Long> ids;
}
