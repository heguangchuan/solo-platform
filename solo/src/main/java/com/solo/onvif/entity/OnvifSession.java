package com.solo.onvif.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "ONVIF 实时点播会话")
@Data
public class OnvifSession {

    @Schema(description = "业务流 ID（业务主键，onvif_<deviceId>）")
    private String streamId;

    @Schema(description = "选中承载拉流的 ZLM 节点 serverId")
    private String serverId;

    @Schema(description = "ONVIF 设备主键（关联 OnvifDevice.id）")
    private Long deviceId;

    @Schema(description = "ZLM addStreamProxy 返回的 key，用于 delStreamProxy")
    private String proxyKey;

    @Schema(description = "会话开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
}
