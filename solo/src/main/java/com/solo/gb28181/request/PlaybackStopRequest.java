package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "停止回放请求")
@Data
public class PlaybackStopRequest {

    @Schema(description = "流 ID（回放 start 响应中返回）")
    private String streamId;
}
