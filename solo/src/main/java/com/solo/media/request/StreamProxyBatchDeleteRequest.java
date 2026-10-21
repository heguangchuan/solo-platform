package com.solo.media.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "拉流代理批量删除请求")
@Data
public class StreamProxyBatchDeleteRequest {

    @Schema(description = "代理主键 ID 列表（必填；可任意长度）")
    private List<Long> ids;
}
