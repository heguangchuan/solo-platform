package com.solo.gb28181.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 历史回放响应（在 {@link PlayResultResponse} 之上加回放时段元数据）
 *
 * <p>对应 {@code POST /api/gb28181/playback/start}。</p>
 *
 * <p>前端时间轴用 {@link #startEpochSec} / {@link #endEpochSec} / {@link #durationSec} 渲染。</p>
 */
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
