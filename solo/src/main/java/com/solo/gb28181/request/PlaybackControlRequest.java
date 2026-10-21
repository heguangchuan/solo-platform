package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "回放控制请求")
@Data
public class PlaybackControlRequest {

    @Schema(description = "流 ID（回放 start 响应中返回）")
    private String streamId;

    @Schema(description = "动作类型：pause / resume / seek / speed")
    private String action;

    @Schema(description = "按 action 不同含义不同；pause/resume 时可省")
    private String param;
}
