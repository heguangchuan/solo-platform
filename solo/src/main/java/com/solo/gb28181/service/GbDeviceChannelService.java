package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.GbDeviceChannelPageRequest;
import com.solo.gb28181.response.GbDeviceChannelResponse;
import com.solo.gb28181.response.GbDeviceChannelTreeNodeResponse;

import java.util.List;

/**
 * 国标设备通道服务
 *
 * <p>简单按字段查询直接走 {@code lambdaQuery()}，本接口只保留"业务动作"。</p>
 */
public interface GbDeviceChannelService extends IService<GbDeviceChannel> {

    Page<GbDeviceChannelResponse> pageQuery(GbDeviceChannelPageRequest request);

    /**
     * 手动新增通道（页面录入）。强制 status=未注册、source=系统新增，并按 deviceDbId 回填 deviceId。
     */
    void createByManual(GbDeviceChannel channel);

    /**
     * 手动编辑通道。只更新可编辑资料字段，绝不覆盖 status/source 及设备归属。
     */
    void updateByManual(GbDeviceChannel channel);

    /**
     * 逻辑删除单条通道（页面删除按钮）
     */
    void deleteById(Long id);

    /**
     * 批量保存或更新（Catalog 同步入口）
     * <p>按 (device_id, channel_id) 唯一约束 upsert</p>
     */
    void saveOrUpdateBatch(List<GbDeviceChannel> channels);

    /**
     * 删除某个设备的所有通道（设备被删除时联动）
     */
    void deleteByDeviceDbId(Long deviceDbId);

    /**
     * 批量删除通道，删除后更新所属设备的通道计数
     */
    void deleteBatch(List<Long> ids);

    /**
     * 获取设备通道树（用于监控墙左侧树形选择）
     * 按 设备 -> 通道 层级组装
     */
    List<GbDeviceChannelTreeNodeResponse> getDeviceChannelTree();
}
