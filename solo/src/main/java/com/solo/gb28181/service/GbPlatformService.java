package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbPlatform;
import com.solo.gb28181.request.GbPlatformPageRequest;
import com.solo.gb28181.response.GbPlatformVO;

import java.util.List;

/**
 * <p><b>语义已从白名单翻转为黑名单</b>：设备与下级平台默认自由注册（注册时机由对端决定，
 * 白名单模式不现实），只有在 {@code gb_platform} 登记且 {@code enabled=0} 的国标 ID 会被
 * SIP 入口拦截。鉴权与本表无关，统一由 {@code gb28181.password} 决定。</p>
 *
 * <p>拦截点两处，都走 {@link #isBlocked} 的 Redis Set 缓存：</p>
 * <ul>
 *   <li>{@code RegisterRequestProcessor}：REGISTER 静默丢弃（不回 SIP、不入库、不发事件）</li>
 *   <li>{@code MessageRequestProcessor}：MESSAGE 静默丢弃（挡住"先注册后拉黑"的设备继续推目录/心跳）</li>
 * </ul>
 */
public interface GbPlatformService extends IService<GbPlatform> {

    Page<GbPlatformVO> pageQuery(GbPlatformPageRequest request);

    void createByManual(GbPlatform platform);

    void updateByManual(GbPlatform platform);

    void deleteBatch(List<Long> ids);

    boolean isBlocked(String deviceId);

    void evictBlockedCache();
}
