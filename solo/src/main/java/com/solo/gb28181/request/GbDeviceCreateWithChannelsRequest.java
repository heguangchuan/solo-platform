package com.solo.gb28181.request;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import lombok.Data;

import java.util.List;

/**
 * 手动新增设备 + 同步新增多条通道请求
 *
 * <p>对应 {@code POST /api/gb28181/device/save}。
 * 设备 / 通道的状态与来源由后端强制设定，前端入参的这两个字段会被忽略。</p>
 */
@Data
public class GbDeviceCreateWithChannelsRequest {

    /**
     * 设备基本信息（deviceId / name 必填；状态 / 来源忽略）
     */
    private GbDevice device;

    /**
     * 同步新增的通道列表；可为空（不传或传空数组都表示不加通道）
     */
    private List<GbDeviceChannel> channels;
}