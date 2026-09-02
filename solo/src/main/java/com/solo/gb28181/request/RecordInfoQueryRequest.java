package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 录像查询请求
 *
 * <p>对应 {@code POST /api/gb28181/playback/query}，由服务层把 {@code yyyy-MM-dd HH:mm:ss}
 * 转 ISO-8601 发往设备。设备无录像时返回空数组。</p>
 */
@Schema(description = "录像查询请求")
@Data
public class RecordInfoQueryRequest {

    @Schema(description = "设备国标 20 位 ID")
    private String deviceId;

    @Schema(description = "通道国标 20 位 ID")
    private String channelId;

    @Schema(description = "起始时间 yyyy-MM-dd HH:mm:ss")
    private String startTime;

    @Schema(description = "结束时间 yyyy-MM-dd HH:mm:ss")
    private String endTime;
}
