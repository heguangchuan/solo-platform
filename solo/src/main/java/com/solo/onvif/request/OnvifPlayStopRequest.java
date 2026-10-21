package com.solo.onvif.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "ONVIF 点播停止请求")
@Data
public class OnvifPlayStopRequest {

    @Schema(description = "ZLM 流名（start 响应里的 streamId，约定 onvif_<deviceId>）")
    private String streamId;
}
