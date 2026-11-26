package com.solo.openapi.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class OpenRecordStartResponse {

    @Schema(description = "录制流 ID，停止录制和查询录制态时使用")
    private String streamId;

    public OpenRecordStartResponse() {
    }

    public OpenRecordStartResponse(String streamId) {
        this.streamId = streamId;
    }
}
