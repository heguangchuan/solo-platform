package com.solo.gb28181.request;

import lombok.Data;

/**
 * 录像查询请求（RecordInfo Query）
 */
@Data
public class RecordInfoQueryRequest {

    /** 设备国标 ID（20 位） */
    private String deviceId;

    /** 通道国标 ID（20 位） */
    private String channelId;

    /** 起始时间 {@code yyyy-MM-dd HH:mm:ss}，由 service 内部转 ISO-8601 */
    private String startTime;

    /** 结束时间 {@code yyyy-MM-dd HH:mm:ss} */
    private String endTime;
}
