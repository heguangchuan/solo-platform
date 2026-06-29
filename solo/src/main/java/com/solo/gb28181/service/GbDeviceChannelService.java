package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.GbDeviceChannelPageRequest;

import java.util.List;

/**
 * 国标设备通道服务
 */
public interface GbDeviceChannelService extends IService<GbDeviceChannel> {

    Page<GbDeviceChannel> pageQuery(GbDeviceChannelPageRequest request);

    /**
     * 按设备主键列出全部通道
     */
    List<GbDeviceChannel> listByDeviceDbId(Long deviceDbId);

    /**
     * 按设备国标ID + 通道国标ID 取单条
     */
    GbDeviceChannel getByDeviceAndChannel(String deviceId, String channelId);

    /**
     * 批量保存或更新（Catalog 同步入口）
     * <p>按 (device_db_id, channel_id) 唯一约束 upsert</p>
     */
    void saveOrUpdateBatch(List<GbDeviceChannel> channels);

    /**
     * 删除某个设备的所有通道（设备被删除时联动）
     */
    void deleteByDeviceDbId(Long deviceDbId);
}
