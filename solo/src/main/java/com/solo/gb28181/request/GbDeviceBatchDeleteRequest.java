package com.solo.gb28181.request;

import lombok.Data;

import java.util.List;

/**
 * 批量删除设备请求
 *
 * <p>对应 {@code POST /api/gb28181/device/batch/delete}，
 * 与单条 {@code DELETE /{deviceId}} 行为一致：删除设备同时连同其名下所有通道。
 * 仅删本平台数据库记录，不影响实际设备本身。</p>
 */
@Data
public class GbDeviceBatchDeleteRequest {

    /**
     * 要删除的设备主键 ID 列表（数据库自增 ID，非国标 deviceId）
     */
    private List<Long> ids;
}
