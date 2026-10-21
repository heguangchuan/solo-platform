package com.solo.media.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "首页概览统计响应体")
@Data
public class HomeOverviewResponse {

    @Schema(description = "国标设备总数（含下级平台）")
    private Long gbDeviceTotal;

    @Schema(description = "国标设备在线数（status=1）")
    private Long gbDeviceOnline;

    @Schema(description = "国标通道总数")
    private Long gbChannelTotal;

    @Schema(description = "国标通道在线数（status=ON）")
    private Long gbChannelOnline;

    @Schema(description = "ONVIF 设备总数")
    private Long onvifDeviceTotal;

    @Schema(description = "ONVIF 设备在线数（status=1）")
    private Long onvifDeviceOnline;

    @Schema(description = "媒体节点总数")
    private Long mediaServerTotal;

    @Schema(description = "媒体节点在线数（status=1）")
    private Long mediaServerOnline;
}
