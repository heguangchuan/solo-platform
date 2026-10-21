package com.solo.gb28181.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "历史回放响应")
@Data
@EqualsAndHashCode(callSuper = true)
public class PlaybackResultResponse extends PlayResultResponse {

    @Schema(description = "回放起始 Unix 时间戳（秒）")
    private Long startEpochSec;

    @Schema(description = "回放结束 Unix 时间戳（秒）")
    private Long endEpochSec;

    @Schema(description = "回放总时长（秒） = end - start")
    private Long durationSec;
}
