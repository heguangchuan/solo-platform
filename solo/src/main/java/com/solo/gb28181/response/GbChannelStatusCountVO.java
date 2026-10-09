package com.solo.gb28181.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 国标通道状态统计 VO
 *
 * <p>对应接口 {@code GET /api/gb28181/device-channel/status-count}，全局口径，与列表查询条件无关。
 * 一条 GROUP BY SQL 同时按 parental（通道/分组）+ status 维度返回数量：</p>
 * <ul>
 *   <li>分组（parental=1）status 固定为 "ON"，与通道在线态同值，故必须带 parental 区分；</li>
 *   <li>通道（parental=0）status 为 ON/OFF/UNREGISTERED。</li>
 * </ul>
 */
@Schema(description = "国标通道状态统计")
@Data
public class GbChannelStatusCountVO {

    /** 资源类型：{@code 0=通道}、{@code 1=分组} */
    @Schema(description = "资源类型（0通道/1分组）")
    private Integer parental;

    /** 状态：通道为 {@code ON/OFF/UNREGISTERED}；分组固定为 {@code ON} */
    @Schema(description = "状态（通道 ON/OFF/UNREGISTERED；分组固定 ON）")
    private String status;

    /** 该维度下的数量 */
    @Schema(description = "数量")
    private Long count;
}
