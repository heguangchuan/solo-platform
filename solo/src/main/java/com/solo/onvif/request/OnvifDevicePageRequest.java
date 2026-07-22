package com.solo.onvif.request;

import lombok.Data;

/**
 * ONVIF 设备分页查询请求
 */
@Data
public class OnvifDevicePageRequest {

    private Long page = 1L;
    private Long pageSize = 20L;

    /** 显示名（模糊） */
    private String name;

    /** IP（模糊） */
    private String host;

    /** 在线状态：0 离线 1 在线 */
    private Integer status;
}
