package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "云端录像计划分页查询请求")
@Data
public class GbRecordPlanPageRequest {

    @Schema(description = "当前页码（从 1 开始）")
    private Long page = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 50L;

    @Schema(description = "所属设备国标 ID")
    private String deviceId;

    @Schema(description = "通道国标 ID")
    private String channelId;

    @Schema(description = "启用状态：1 启用、0 停用、null 全部")
    private Integer enabled;
}
