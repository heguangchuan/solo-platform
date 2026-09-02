package com.solo.onvif.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * ONVIF 点播开始请求
 *
 * <p>对应 {@code POST /api/onvif/play/start}。
 * 用设备主键定位 RTSP 源，streamId 由服务端按 {@code onvif_<deviceId>} 约定生成，
 * 前端不需传入；调用成功后用返回的 streamId 取 ZLM 播放地址。</p>
 */
@Schema(description = "ONVIF 点播开始请求")
@Data
public class OnvifPlayStartRequest {

    @Schema(description = "ONVIF 设备主键 ID")
    private Long deviceId;
}
