package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.request.GbDevicePageRequest;

/**
 * 国标设备服务
 */
public interface GbDeviceService extends IService<GbDevice> {

    Page<GbDevice> pageQuery(GbDevicePageRequest request);

    GbDevice getByDeviceId(String deviceId);

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
