package com.solo.gb28181.request;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "手动新增设备 + 同步新增多条通道请求")
@Data
public class GbDeviceCreateWithChannelsRequest {

    @Schema(description = "设备基本信息（deviceId / name 必填）")
    private GbDevice device;

    @Schema(description = "同步新增的通道列表；可为空")
    private List<GbDeviceChannel> channels;
}