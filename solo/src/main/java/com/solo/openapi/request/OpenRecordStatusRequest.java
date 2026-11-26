package com.solo.openapi.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OpenRecordStatusRequest {

    @Schema(description = "streamId", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "streamId 不能为空")
    private String streamId;
}
