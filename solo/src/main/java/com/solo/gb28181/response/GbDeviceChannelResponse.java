package com.solo.gb28181.response;

import com.solo.gb28181.entity.GbDeviceChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "通道分页响应项")
@Data
@EqualsAndHashCode(callSuper = true)
public class GbDeviceChannelResponse extends GbDeviceChannel {
}
