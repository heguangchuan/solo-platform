package com.solo.gb28181.response;

import lombok.Data;

import java.util.List;

/**
 * 设备通道树节点响应
 *
 * @author solo
 */
@Data
public class GbDeviceChannelTreeNodeResponse {

    /**
     * 节点ID
     */
    private String id;

    /**
     * 节点显示标签
     */
    private String label;

    /**
     * 子节点
     */
    private List<GbDeviceChannelTreeNodeResponse> children;

    /**
     * 设备国标ID（设备节点才有值）
     */
    private String deviceId;

    /**
     * 通道国标ID（通道节点才有值）
     */
    private String channelId;

    /**
     * 是否为设备节点
     */
    private Boolean isDevice;

    /**
     * 设备状态（设备节点才有值）：0离线 1在线 2未注册
     */
    private Integer status;

    /**
     * 通道状态（通道节点才有值）：ON/OFF/UNREGISTERED
     */
    private String channelStatus;
}
