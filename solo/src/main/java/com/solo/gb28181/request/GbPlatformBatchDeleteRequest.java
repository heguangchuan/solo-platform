package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "批量删除下级级联平台请求")
@Data
public class GbPlatformBatchDeleteRequest {

    @Schema(description = "要删除的平台主键 ID 列表")
    private List<Long> ids;
}