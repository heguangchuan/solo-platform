package com.solo.openapi.controller.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 开放接口对外的设备信息。只挑对接方用得上的字段，不直接把内部实体丢出去。
 */
@Schema(description = "设备信息")
public record OpenDeviceVO(
        @Schema(description = "设备国标 20 位 ID") String deviceId,
        @Schema(description = "设备名称") String name,
        @Schema(description = "厂商") String manufacturer,
        @Schema(description = "型号") String model,
        @Schema(description = "在线状态：0 离线、1 在线、2 未注册") Integer status,
        @Schema(description = "传输模式：UDP/TCP") String transport,
        @Schema(description = "设备 IP") String ip,
        @Schema(description = "注册端口") Integer port,
        @Schema(description = "最近注册时间") @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime registerTime,
        @Schema(description = "最近心跳时间") @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime keepaliveTime) {
}
