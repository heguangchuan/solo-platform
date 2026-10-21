package com.solo.onvif.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "ONVIF 点播开始请求")
@Data
public class OnvifPlayStartRequest {

    @Schema(description = "ONVIF 设备主键 ID")
    private Long deviceId;
}
