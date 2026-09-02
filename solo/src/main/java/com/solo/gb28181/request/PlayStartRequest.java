package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 实时点播开始请求
 * <p>对应 {@code POST /api/gb28181/play/start}。</p>
 * <p>点播提交后立即返回带 streamId 的 INVITING 状态，
 * 前端按 {@code GET /xxx/status} 轮询到 READY 取播放地址。</p>
 */
@Schema(description = "实时点播开始请求")
@Data
public class PlayStartRequest {

    @Schema(description = "设备国标 20 位 ID")
    private String deviceId;

    @Schema(description = "通道国标 20 位 ID")
    private String channelId;

    @Schema(description = "收流模式：UDP / TCP-passive")
    private String transport = "TCP-passive";

    @Schema(description = "码流标识 token：留空/null = 主码流（子码流默认 streamnumber:1）")
    private String streamNumber = "streamnumber:1";
}
