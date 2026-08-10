package com.solo.gb28181.response;

import com.solo.gb28181.entity.GbPlatform;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 接入禁用名单列表响应：在 {@link GbPlatform} 黑名单字段之上，回填其关联设备
 * （{@code gb_device.device_id == platform_id}）的运行态字段。
 * <p>被拉黑的 ID 可能之前注册过，展示在线状态/心跳便于运维确认"拉黑后是否真的断了"。
 * 这些字段不在 gb_platform 表冗余，由 service 关联查询回填。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GbPlatformVO extends GbPlatform {

    /**
     * 在线状态（取关联设备的 status）：0 离线 1 在线 2 未注册（未接入）
     */
    private Integer status;

    /**
     * 最后心跳时间（取关联设备）
     */
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime keepaliveTime;

    /**
     * 该平台下同步来的通道总数（取关联设备）
     */
    private Integer channelCount;
}