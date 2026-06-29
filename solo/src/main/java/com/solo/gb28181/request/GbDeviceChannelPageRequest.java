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

    /** 父节点ID（按目录树查询） */
    private String parentId;
}
