package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "停止点播请求")
@Data
public class PlayStopRequest {

    @Schema(description = "流 ID")
    private String streamId;
}
