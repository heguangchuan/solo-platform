package com.solo.gb28181.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备移动位置分页 / 轨迹查询请求
 *
 * <p>{@code POST /api/gb28181/position/page} 用分页字段；
 * {@code POST /api/gb28181/position/track} 复用本 DTO，按 deviceId + 时间段返回时间升序轨迹点。</p>
 */
@Schema(description = "设备移动位置分页查询请求")
@Data
public class GbMobilePositionPageRequest {

    @Schema(description = "当前页码（从 1 开始）")
    private Long page = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    @Schema(description = "设备国标 ID（前端设备下拉精确选择）")
    private String deviceId;

    @Schema(description = "轨迹查询起始时间（按设备定位时间）")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Schema(description = "轨迹查询结束时间（按设备定位时间）")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
