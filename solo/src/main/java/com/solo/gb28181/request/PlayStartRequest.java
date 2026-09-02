package com.solo.gb28181.request;

import lombok.Data;

/**
 * 实时点播开始请求
 * <p>对应 {@code POST /api/gb28181/play/start}。</p>
 * <p>点播提交后立即返回带 streamId 的 INVITING 状态，
 * 前端按 {@code GET /xxx/status} 轮询到 READY 取播放地址。</p>
 */
@Data
public class PlayStartRequest {

    /**
     * 设备国标 20 位 ID（必填）
     */
    private String deviceId;

    /**
     * 通道国标 20 位 ID（必填）
     */
    private String channelId;

    /**
     * 收流模式：{@code UDP} / {@code TCP-passive}；默认 {@code TCP-passive}
     */
    private String transport = "TCP-passive";

    /**
     * 码流标识 token：留空 / null 表示主码流（子码流传厂商约定的 wire token）。
     * <p>取非空时按各厂商原样附加到发往设备的 INVITE 信令通道段后缀，
     * 由前端 / 配置给出，后端不解析其含义，以兼容各厂商写法（如 {@code 1} / {@code stream:sub} / {@code streamnumber:1}）。</p>
     * <p>默认 {@code "streamnumber:1"}（子码流）。</p>
     */
    private String streamNumber = "streamnumber:1";
}
