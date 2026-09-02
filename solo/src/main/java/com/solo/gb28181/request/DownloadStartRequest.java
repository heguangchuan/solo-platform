package com.solo.gb28181.request;

import lombok.Data;

/**
 * 开始录像下载请求
 *
 * <p>对应 {@code POST /api/gb28181/download/start}。</p>
 *
 * <p>下载字段集与历史回放一致，唯一差异是 {@link #speed}（下载倍速）。</p>
 */
@Data
public class DownloadStartRequest {

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
     * 下载倍速：{@code 1} / {@code 2} / {@code 4} / {@code 8}，默认 {@code 1}（原速）。
     * 部分设备不支持倍速会忽略该参数，按原速下载。
     */
    private Integer speed = 1;

    /**
     * 码流 token：空 / 不传 = 主码流，非空 = 子码流。
     * 与点播 / 回放共用一套写法。
     */
    private String streamNumber;
}
