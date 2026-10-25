package com.solo.openapi.controller.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 开放接口对外的通道信息。
 */
@Schema(description = "通道信息")
public record OpenChannelVO(
        @Schema(description = "通道国标 20 位 ID") String channelId,
        @Schema(description = "所属设备国标 ID") String deviceId,
        @Schema(description = "通道名称") String name,
        @Schema(description = "在线状态：ON / OFF / UNREGISTERED") String status,
        @Schema(description = "是否分组：0 通道、1 分组") Integer parental,
        @Schema(description = "父分组国标 ID") String parentId,
        @Schema(description = "厂商") String manufacturer,
        @Schema(description = "型号") String model,
        @Schema(description = "云台类型：0 未知、1 球机、2 半球、3 固定枪机、4 遥控枪机") Integer ptzType,
        @Schema(description = "经度（WGS84，原样透出）") Double longitude,
        @Schema(description = "纬度") Double latitude) {
}
