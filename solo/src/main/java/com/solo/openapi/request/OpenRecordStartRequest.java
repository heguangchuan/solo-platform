package com.solo.openapi.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OpenRecordStartRequest {

    @Schema(description = "国标设备编号，或开放平台设备编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "deviceId 不能为空")
    private String deviceId;

    @Schema(description = "通道编号，或开放平台通道编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "channelId 不能为空")
    private String channelId;

    @Schema(description = "收流方式：udp/tcp-passive/tcp-active，留空默认 udp")
    private String transport;

    @Schema(description = "码流标识：主码流留空，子码流传 1")
    private String streamNumber;
}
