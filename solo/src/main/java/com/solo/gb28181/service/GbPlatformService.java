package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbPlatform;
import com.solo.gb28181.request.GbPlatformPageRequest;
import com.solo.gb28181.response.GbPlatformVO;

import java.util.List;

/**
 * 国标接入禁用名单服务（黑名单管理）
 *
 * <p><b>语义已从白名单翻转为黑名单</b>：设备与下级平台默认自由注册（注册时机由对端决定，
 * 白名单模式不现实），只有在 {@code gb_platform} 登记且 {@code enabled=0} 的国标 ID 会被
 * SIP 入口拦截。鉴权与本表无关，统一由 {@code gb28181.password} 决定（见 ADR-022）。</p>
 *
 * <p>拦截点两处，都走 {@link #isBlocked} 的 Redis Set 缓存：</p>
 * <ul>
 *   <li>{@code RegisterRequestProcessor}：REGISTER 静默丢弃（不回 SIP、不入库、不发事件）</li>
 *   <li>{@code MessageRequestProcessor}：MESSAGE 静默丢弃（挡住"先注册后拉黑"的设备继续推目录/心跳）</li>
 * </ul>
 */
public interface GbPlatformService extends IService<GbPlatform> {

    /**
     * 分页查询禁用名单，并关联 gb_device（device_id == platform_id）回填在线状态等运行态字段
     * （被拉黑的设备可能之前注册过，运行态用于运维判断"拉黑是否生效"）
     */
    Page<GbPlatformVO> pageQuery(GbPlatformPageRequest request);

    /**
     * 后台录入禁用名单。platformId 20 位校验 + 唯一校验；写入后失效缓存。
     */
    void createByManual(GbPlatform platform);

    /**
     * 后台编辑禁用名单（改备注 / 原因 / 临时放行开关）。platformId 不可改；改完失效缓存。
     */
    void updateByManual(GbPlatform platform);

    /**
     * 批量删除禁用名单（仅删名单记录，不动已注册的 gb_device 行）；删完失效缓存。
     */
    void deleteBatch(List<Long> ids);

    /**
     * 判断某国标 ID 是否被禁用接入。SIP 入口高频调用，走 {@code gb28181:device:blocked}
     * Redis Set 缓存（TTL 5 分钟，增删改主动失效）；Redis 不可用时回退查库。
     *
     * @return true 表示已被拉黑，调用方应静默丢弃该报文
     */
    boolean isBlocked(String deviceId);

    /**
     * 主动失效禁用名单缓存（增删改后调用）。
     */
    void evictBlockedCache();
}
