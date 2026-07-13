package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.request.GbDevicePageRequest;

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
     */
    void createByManual(GbDevice device);

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
}
