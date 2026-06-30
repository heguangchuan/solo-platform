package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.GbDeviceChannelPageRequest;
import com.solo.gb28181.response.GbDeviceChannelResponse;

import java.util.List;

/**
 * 国标设备通道服务
 *
 * <p>简单按字段查询直接走 {@code lambdaQuery()}，本接口只保留"业务动作"。</p>
 */
public interface GbDeviceChannelService extends IService<GbDeviceChannel> {

    Page<GbDeviceChannelResponse> pageQuery(GbDeviceChannelPageRequest request);

    /**
     * 批量保存或更新（Catalog 同步入口）
     * <p>按 (device_id, channel_id) 唯一约束 upsert</p>
     */
    void saveOrUpdateBatch(List<GbDeviceChannel> channels);

    /**
     * 删除某个设备的所有通道（设备被删除时联动）
     */
    void deleteByDeviceDbId(Long deviceDbId);
}
