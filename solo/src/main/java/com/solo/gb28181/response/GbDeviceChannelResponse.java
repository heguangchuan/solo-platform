package com.solo.gb28181.response;

import com.solo.gb28181.entity.GbDeviceChannel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通道分页响应：在 {@link GbDeviceChannel} entity 之上扩出"所属设备名称"等冗余字段，
 * 避免 entity 直接挂 {@code @TableField(exist=false)} 污染 ORM 模型。
 * <p>所有 entity 字段通过继承透传给前端；新增的 {@link #deviceName} 由 service 分页查询后回填。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GbDeviceChannelResponse extends GbDeviceChannel {

    /** 所属设备名称（按 deviceId 反查 gb_device.name 回填，不入库） */
    private String deviceName;

    /** 所属虚拟目录名称（按 groupId 反查 gb_group.name 回填，不入库） */
    private String groupName;
}
