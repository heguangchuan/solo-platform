package com.solo.openapi.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OpenRecordStopRequest {

    @Schema(description = "开始录制时返回的 streamId", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "streamId 不能为空")
    private String streamId;
}
