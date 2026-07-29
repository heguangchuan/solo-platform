package com.solo.gb28181.request;

import lombok.Data;

/**
 * 国标设备通道分页查询请求
 */
@Data
public class GbDeviceChannelPageRequest {

    private Long page = 1L;
    private Long pageSize = 20L;

    /** 设备国标ID（精确） */
    private String deviceId;

    /** 通道国标ID（模糊） */
    private String channelId;

    /** 通道名称（模糊） */
    private String name;

    /** 在线状态 ON / OFF */
    private String status;

    /** 所属资源目录分组ID（按分组筛选通道） */
    private Long groupId;

    /** 数据来源：1 系统新增，2 表格导入，3 设备注册 */
    private Integer source;
}
