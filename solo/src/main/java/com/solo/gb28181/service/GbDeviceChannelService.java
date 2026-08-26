package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.GbDeviceChannelPageRequest;
import com.solo.gb28181.response.GbDeviceChannelResponse;
import com.solo.gb28181.response.MapMarkerResponse;

import java.util.List;

/**
 * 国标设备通道服务
 *
 * <p>简单按字段查询直接走 {@code lambdaQuery()}，本接口只保留"业务动作"。</p>
 */
public interface GbDeviceChannelService extends IService<GbDeviceChannel> {

    Page<GbDeviceChannelResponse> pageQuery(GbDeviceChannelPageRequest request);

    /**
     * 手动新增通道（页面录入）。强制 status=未注册、source=系统新增，按 {@code channel.deviceId}（GB 码）关联设备。
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
     * <p>按 (device_id, channel_id) 唯一约束 upsert；分组（parental=1）走应用层 id 复用</p>
     */
    void saveOrUpdateBatch(List<GbDeviceChannel> channels);

    /**
     * 删除某个设备的所有通道（设备被删除时联动）
     * @param deviceId 设备国标ID（GB 码字符串）
     */
    void deleteByDeviceId(String deviceId);

    /**
     * 批量删除通道，删除后更新所属设备的通道计数
     */
    void deleteBatch(List<Long> ids);

    /**
     * 首页地图打点查询：按视口矩形范围返回该范围内的通道 marker 列表，
     * 同时返回数据库里"有坐标的通道总数"（不受视口限制，侧栏 KPI 用）。
     *
     * <p>单次最多返回 5000 条（数据库 SQL LIMIT 限制），超过会被截断，
     * 调用方按需提示用户"放大以查看更多"。bounds 在调用前需校验合法。</p>
     *
     * @param minLng 视口西边经度（含）
     * @param minLat 视口南边纬度（含）
     * @param maxLng 视口东边经度（含）
     * @param maxLat 视口北边纬度（含）
     * @return 紧凑字段的 marker 响应（含总数 + marker 列表）
     */
    MapMarkerResponse queryMapMarkers(double minLng, double minLat, double maxLng, double maxLat);

}
