package com.solo.onvif.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * ONVIF 点播停止请求
 *
 * <p>对应 {@code POST /api/onvif/play/stop}。
 * 幂等 —— streamId 不存在或会话已清理时直接返回成功，不抛异常，前端可放心调用。</p>
 */
@Schema(description = "ONVIF 点播停止请求")
@Data
public class OnvifPlayStopRequest {

    @Schema(description = "ZLM 流名（start 响应里的 streamId，约定 onvif_<deviceId>）")
    private String streamId;
}
