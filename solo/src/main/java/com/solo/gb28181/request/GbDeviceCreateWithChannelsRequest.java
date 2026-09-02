package com.solo.gb28181.request;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 手动新增设备 + 同步新增多条通道请求
 *
 * <p>对应 {@code POST /api/gb28181/device/save}。
 * 设备 / 通道的状态与来源由后端强制设定，前端入参的这两个字段会被忽略。</p>
 */
@Schema(description = "手动新增设备 + 同步新增多条通道请求")
@Data
public class GbDeviceCreateWithChannelsRequest {

    @Schema(description = "设备基本信息（deviceId / name 必填）")
    private GbDevice device;

    @Schema(description = "同步新增的通道列表；可为空")
    private List<GbDeviceChannel> channels;
}