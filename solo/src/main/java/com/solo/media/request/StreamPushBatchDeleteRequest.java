package com.solo.media.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "推流批量删除请求")
@Data
public class StreamPushBatchDeleteRequest {

    @Schema(description = "推流主键 ID 列表（必填；可任意长度）")
    private List<Long> ids;
}
