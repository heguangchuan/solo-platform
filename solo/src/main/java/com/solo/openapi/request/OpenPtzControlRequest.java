package com.solo.openapi.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "开放API云台控制请求")
@Data
public class OpenPtzControlRequest {

    @Schema(description = "设备国标 20 位 ID")
    @NotBlank(message = "设备国标ID不能为空")
    private String deviceId;

    @Schema(description = "通道国标 20 位 ID")
    @NotBlank(message = "通道国标ID不能为空")
    private String channelId;

    @Schema(description = "云台指令：left/right/up/down/upleft/upright/downleft/downright/zoomin/zoomout/stop")
    @NotBlank(message = "云台指令不能为空")
    private String command;

    @Schema(description = "移动速度 0-255，默认 100")
    private Integer speed = 100;

    @Schema(description = "缩放速度 0-15，默认 5")
    private Integer zoomSpeed = 5;
}
