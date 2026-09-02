package com.solo.gb28181.request;

import lombok.Data;

/**
 * 开始回放请求
 *
 * <p>对应 {@code POST /api/gb28181/playback/start}。回放成功后返回 {@link com.solo.gb28181.response.PlaybackResultResponse}。</p>
 */
@Data
public class PlaybackStartRequest {

    /** 设备国标 20 位 ID（必填） */
    private String deviceId;

    /** 通道国标 20 位 ID（必填） */
    private String channelId;

    /** 起始时间 {@code yyyy-MM-dd HH:mm:ss}（必填） */
    private String startTime;

    /** 结束时间 {@code yyyy-MM-dd HH:mm:ss}（必填） */
    private String endTime;

    /** 收流模式：{@code UDP} / {@code TCP-passive} / {@code TCP-active}；默认 {@code TCP-passive} */
    private String transport = "TCP-passive";

    /**
     * 码流 token：空 / 不传 = 主码流，非空 = 子码流（按厂商原样写进 SIP 信令）。
     * 各厂商 / 国标平台写法不一（{@code 1} / {@code stream:1} / {@code streamnumber:1} / {@code sub} 等），
     * 前端按设备能力选枚举透传，设备据此选主 / 子码流做回放。
     */
    private String streamNumber;
}
