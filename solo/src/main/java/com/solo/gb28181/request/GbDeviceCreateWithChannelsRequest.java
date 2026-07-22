package com.solo.gb28181.request;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import lombok.Data;

import java.util.List;

/**
 * 手动新增设备 + 同步新增多条通道请求
 */
@Data
public class GbDeviceCreateWithChannelsRequest {

    /**
     * 设备基本信息
     */
    private GbDevice device;

    /**
     * 同步新增的通道列表，可以为空
     */
    private List<GbDeviceChannel> channels;
}