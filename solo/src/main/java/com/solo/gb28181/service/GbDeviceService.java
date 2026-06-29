package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.request.GbDevicePageRequest;

/**
 * 国标设备服务
 */
public interface GbDeviceService extends IService<GbDevice> {

    /**
     * 分页查询设备
     */
    Page<GbDevice> pageQuery(GbDevicePageRequest request);

    /**
     * 根据国标 ID 查询设备
     */
    GbDevice getByDeviceId(String deviceId);

    /**
     * 处理一次设备注册（首次新增 / 已有则更新接入信息）
     *
     * @param deviceId  设备国标 ID
     * @param ip        来源 IP
     * @param port      来源端口
     * @param transport 传输协议
     * @param expires   注册有效期（秒）
     */
    void onRegister(String deviceId, String ip, int port, String transport, int expires);

    /**
     * 处理一次设备注销（Expires=0 的 REGISTER）
     */
    void onUnregister(String deviceId);

    /**
     * 处理一次心跳（Keepalive）
     */
    void onKeepalive(String deviceId);
}
