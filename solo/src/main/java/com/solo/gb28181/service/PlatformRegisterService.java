package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbParentPlatform;

/**
 * 级联注册服务（本平台作为下级向上级平台注册 / 保活）
 */
public interface PlatformRegisterService {

    /**
     * 对所有启用的上级平台发起首次注册。
     */
    void registerAll();

    /**
     * 对启用但当前离线的上级平台重试注册（上级恢复后自动上线）。
     */
    void registerOffline();

    /**
     * 对所有启用的上级平台重发注册以在 expires 过期前刷新。
     */
    void refreshAll();

    /**
     * 对所有在线的上级平台发送心跳；连续超时达到阈值判离线。
     */
    void keepaliveAll();

    /**
     * 注册成功回调：回填在线状态、清心跳超时计数。
     */
    void online(GbParentPlatform platform);

    /**
     * 注册失败 / 注销 / 心跳超时回调：回填离线状态。
     */
    void offline(GbParentPlatform platform);

    /**
     * 对单个上级平台发起注册。
     */
    void register(GbParentPlatform platform);

    /**
     * 关停时对所有在线上级发送注销（expires=0）。
     */
    void unregisterAll();
}
