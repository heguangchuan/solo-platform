package com.solo.gb28181.request;

import lombok.Data;

/**
 * 开始回放请求
 */
@Data
public class PlaybackStartRequest {

    private String deviceId;

    private String channelId;

    /** 起始时间 {@code yyyy-MM-dd HH:mm:ss} */
    private String startTime;

    /** 结束时间 {@code yyyy-MM-dd HH:mm:ss} */
    private String endTime;

    /** 收流模式：{@code UDP} / {@code TCP-passive} / {@code TCP-active}；缺省 {@code TCP-passive}（与点播对齐） */
    private String transport = "TCP-passive";

    /**
     * 码流 token：空/不传 = 主码流（SDP 不写 {@code a=} 行），非空 = 子码流（原样写进 {@code a=<token>}）。
     * 各厂商 / 国标平台写法不一（{@code 1} / {@code stream:1} / {@code streamnumber:1} / {@code sub} 等），
     * 前端写死枚举透传给设备，设备据此选主/子码流做回放。
     */
    private String streamNumber;
}
