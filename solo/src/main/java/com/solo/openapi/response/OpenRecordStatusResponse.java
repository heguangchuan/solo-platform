package com.solo.openapi.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class OpenRecordStatusResponse {

    @Schema(description = "streamId")
    private String streamId;

    @Schema(description = "录制态：RECORDING / IDLE / UNAVAILABLE / NO_SESSION")
    private String status;

    @Schema(description = "承载该录制流的 ZLM 节点")
    private String serverId;
}
