package com.solo.gb28181.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 国标设备状态统计 VO
 *
 * <p>对应接口 {@code GET /api/gb28181/device/status-count}，全局口径，与列表查询条件无关。
 * 一条 GROUP BY SQL 同时返回各状态设备数量。</p>
 */
@Schema(description = "国标设备状态统计")
@Data
public class GbDeviceStatusCountVO {

    /** 设备状态：{@code 1=在线}、{@code 0=离线}、{@code 2=未注册} */
    @Schema(description = "状态（1在线/0离线/2未注册）")
    private Integer status;

    /** 该状态下的设备数 */
    @Schema(description = "设备数")
    private Long count;
}
