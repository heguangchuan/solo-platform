package com.solo.gb28181.response;

import com.solo.gb28181.entity.GbPlatform;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 接入禁用名单列表响应项：在黑名单字段之上，附加关联设备的运行态字段
 *
 * <p>对应 {@code POST /api/gb28181/platform/page} 返回项。</p>
 * <p>被拉黑的 ID 可能之前注册过，展示在线状态/心跳便于运维确认"拉黑后是否真的断了"。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GbPlatformVO extends GbPlatform {

    /**
     * 在线状态（取关联设备的 status）：{@code 0} 离线、{@code 1} 在线、{@code 2} 未注册（未接入）
     */
    private Integer status;

    /**
     * 最后心跳时间，格式 {@code yyyy-MM-dd HH:mm:ss}（取关联设备）
     */
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime keepaliveTime;

}