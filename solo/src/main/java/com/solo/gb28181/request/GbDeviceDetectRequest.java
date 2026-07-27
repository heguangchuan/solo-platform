package com.solo.gb28181.request;

import lombok.Data;

import java.util.List;

/**
 * 批量检测设备存活请求
 */
@Data
public class GbDeviceDetectRequest {

    /**
     * 要检测的设备主键 ID 列表
     */
    private List<Long> ids;
}
