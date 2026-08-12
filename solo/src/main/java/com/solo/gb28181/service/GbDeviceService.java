package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.GbDevicePageRequest;
import com.solo.gb28181.response.GbDeviceDetectResponse;

/**
 * 国标设备服务
 *
 * <p>仅暴露"业务动作"。简单按字段查询（如按 deviceId 取一行）由调用方直接走
 * {@code lambdaQuery().eq(GbDevice::getDeviceId, ...).one()}，不在此处增加薄方法。</p>
 */
public interface GbDeviceService extends IService<GbDevice> {

    Page<GbDevice> pageQuery(GbDevicePageRequest request);

    /**
     * 手动新增设备（页面录入）。强制 status=未注册、source=系统新增，忽略入参 status/source。
     * @param channels 同步新增的多条通道，可以为空
     */
    void createByManual(GbDevice device, java.util.List<GbDeviceChannel> channels);

    /**
     * 手动编辑设备。只更新可编辑的资料字段，绝不覆盖 status/source 及 SIP 运行期字段
     * （registerTime/keepaliveTime/ip/port/hostAddress 等）。
     */
    void updateByManual(GbDevice device);

    /**
     * 设备上线（注册成功 / 心跳重新上线）
     * <p>device 已设好 ip/port/localIp/transport/expires 等字段，本方法负责持久化 + Redis TTL</p>
     */
    void online(GbDevice device);

    /**
     * 设备离线
     *
     * @param deviceId 设备国标 ID
     * @param reason   离线原因（日志/告警用）
     */
    void offline(String deviceId, String reason);

    /**
     * 心跳收到后更新心跳时间和 Redis TTL
     */
    void keepalive(GbDevice device);

    /**
     * 批量删除设备，连同其名下所有通道
     */
    void deleteBatch(java.util.List<Long> ids);

    /**
     * 失效设备档案缓存（{@link com.solo.gb28181.constant.GbRedisKeys#DEVICE_PROFILE_PREFIX}）。
     * <p>凡是改了 {@code gb_device} 行的写路径（online / offline / updateByManual / deleteBatch
     * 以及 {@code CatalogSyncSupport} final flush 改 {@code lastCatalogSyncTime}）
     * 都必须调一次，否则下次 SIP 入站按 {@code deviceId} 查缓存会拿到旧值。TTL 是兜底。</p>
     */
    void evictProfileCache(String deviceId);

    /**
     * 批量探测设备存活
     * <p>向每台设备发送 DeviceInfo 查询（SIP MESSAGE），等待应答。
     * 有应答 → online（写回 status=1 + 刷新 Redis TTL）；
     * 超时/异常 → offline（写回 status=0 + 清 Redis）；
     * 无网络地址（从未注册）→ skipped，不改 status。</p>
     */
    GbDeviceDetectResponse detectBatch(java.util.List<Long> ids);
}
