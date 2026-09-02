package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 批量删除设备请求
 *
 * <p>对应 {@code POST /api/gb28181/device/batch/delete}，
 * 与单条 {@code DELETE /{deviceId}} 行为一致：删除设备同时连同其名下所有通道。
 * 仅删本平台数据库记录，不影响实际设备本身。</p>
 */
@Schema(description = "批量删除设备请求")
@Data
public class GbDeviceBatchDeleteRequest {

    @Schema(description = "要删除的设备主键 ID 列表")
    private List<Long> ids;
}
