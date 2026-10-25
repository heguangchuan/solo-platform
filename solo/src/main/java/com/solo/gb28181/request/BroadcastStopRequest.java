package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "语音广播停止请求")
@Data
public class BroadcastStopRequest {

    @Schema(description = "广播流 ID（开始广播时返回）")
    private String streamId;
}
