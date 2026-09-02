package com.solo.gb28181.response;

import com.solo.gb28181.entity.GbDeviceChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通道分页响应项（透传通道表全部字段）
 *
 * <p>对应 {@code POST /api/gb28181/device-channel/page} 返回项。</p>
 */
@Schema(description = "通道分页响应项")
@Data
@EqualsAndHashCode(callSuper = true)
public class GbDeviceChannelResponse extends GbDeviceChannel {
}
