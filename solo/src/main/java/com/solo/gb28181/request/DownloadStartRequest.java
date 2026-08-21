package com.solo.gb28181.request;

import lombok.Data;

/**
 * 开始录像下载请求
 *
 * <p>与历史回放请求同构，唯一差异是 {@link #speed}（下载倍速）。
 * GB/T 28181-2016 §9.5 DOWNLOAD INVITE 与 §9.4 Playback INVITE 报文几乎相同，
 * 仅 SDP 头多一个 {@code a=downloadspeed:N} 行。</p>
 */
@Data
public class DownloadStartRequest {

    private String deviceId;

    private String channelId;

    /** 起始时间 {@code yyyy-MM-dd HH:mm:ss} */
    private String startTime;

    /** 结束时间 {@code yyyy-MM-dd HH:mm:ss} */
    private String endTime;

    /** 收流模式：{@code UDP} / {@code TCP-passive} / {@code TCP-active}；缺省 {@code TCP-passive}（与点播对齐） */
    private String transport = "TCP-passive";

    /**
     * 下载倍速：{@code 1} / {@code 2} / {@code 4} / {@code 8}，缺省 {@code 1}（原速）。
     * 国标写进 SDP {@code a=downloadspeed:N}，部分设备不支持倍速会忽略该行（原速下载）。
     */
    private Integer speed = 1;

    /**
     * 码流 token：空/不传 = 主码流（SDP 不写 {@code a=} 行），非空 = 子码流。
     * 与点播 / 回放共用一套写法。
     */
    private String streamNumber;
}
