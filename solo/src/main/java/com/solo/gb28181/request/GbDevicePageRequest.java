package com.solo.gb28181.request;

import lombok.Data;

/**
 * 国标设备分页查询请求
 */
@Data
public class GbDevicePageRequest {

    private Long page = 1L;
    private Long pageSize = 20L;

    /**
     * 设备国标 ID（模糊）
     */
    private String deviceId;

    /**
     * 设备名称（模糊）
     */
    private String name;

    /**
     * 在线状态：0 离线，1 在线，2 未注册
     */
    private Integer status;

    /**
     * 数据来源：1 系统新增，2 表格导入，3 设备注册
     */
    private Integer source;
}
