package com.solo.gb28181.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "录像下载响应")
@Data
public class DownloadResultResponse {

    public static final String STATUS_INVITING = "INVITING";
    public static final String STATUS_RECORDING = "RECORDING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_TIMEOUT = "TIMEOUT";

    @Schema(description = "流 ID（与 start 响应的 streamId 一致；前端 GET /status 用它查任务）")
    private String streamId;

    @Schema(description = "当前任务状态（INVITING/RECORDING/READY/FAILED/TIMEOUT）")
    private String status;

    @Schema(description = "起始时间（Unix 秒）")
    private Long startEpochSec;

    @Schema(description = "结束时间（Unix 秒）")
    private Long endEpochSec;

    @Schema(description = "时段总长（秒） = end - start")
    private Long durationSec;

    @Schema(description = "文件就绪后的下载 URL（仅 READY 状态有值；其他为空字符串）")
    private String downloadUrl;

    @Schema(description = "失败原因（仅 FAILED/TIMEOUT 状态有值）")
    private String errorMsg;
}
