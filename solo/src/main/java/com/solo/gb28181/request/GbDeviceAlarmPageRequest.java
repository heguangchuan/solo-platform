package com.solo.gb28181.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备报警分页查询请求
 *
 * <p>对应 {@code POST /api/gb28181/alarm/page}。响应结构为 {@code { total, rows }}。</p>
 */
@Schema(description = "设备报警分页查询请求")
@Data
public class GbDeviceAlarmPageRequest {

    @Schema(description = "当前页码（从 1 开始）")
    private Long page = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    @Schema(description = "设备国标 ID（模糊匹配）")
    private String deviceId;

    @Schema(description = "通道国标 ID（模糊匹配）")
    private String channelId;

    @Schema(description = "报警级别 1~4")
    private Integer alarmPriority;

    @Schema(description = "处置状态：0 未处理、1 已确认、2 已处置、3 误报")
    private Integer handleStatus;

    @Schema(description = "报警开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Schema(description = "报警结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
