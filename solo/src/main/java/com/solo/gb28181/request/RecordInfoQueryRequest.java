package com.solo.gb28181.request;

import lombok.Data;

/**
 * 录像查询请求
 *
 * <p>对应 {@code POST /api/gb28181/playback/query}，由服务层把 {@code yyyy-MM-dd HH:mm:ss}
 * 转 ISO-8601 发往设备。设备无录像时返回空数组。</p>
 */
@Data
public class RecordInfoQueryRequest {

    /** 设备国标 20 位 ID（必填） */
    private String deviceId;

    /** 通道国标 20 位 ID（必填） */
    private String channelId;

    /** 起始时间 {@code yyyy-MM-dd HH:mm:ss}（必填） */
    private String startTime;

    /** 结束时间 {@code yyyy-MM-dd HH:mm:ss}（必填） */
    private String endTime;
}
