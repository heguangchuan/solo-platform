package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "批量删除通道请求")
@Data
public class GbDeviceChannelBatchDeleteRequest {

    @Schema(description = "要删除的通道主键 ID 列表")
    private List<Long> ids;
}
