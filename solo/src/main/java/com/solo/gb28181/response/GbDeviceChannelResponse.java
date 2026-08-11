package com.solo.gb28181.response;

import com.solo.gb28181.entity.GbDeviceChannel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通道分页响应：透传 {@link GbDeviceChannel} entity 全部字段。
 * <p>当前不回填任何冗余字段（{@code deviceName} 已移除——前端不需要）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GbDeviceChannelResponse extends GbDeviceChannel {
}
