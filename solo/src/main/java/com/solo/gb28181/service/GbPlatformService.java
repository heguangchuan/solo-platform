package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbPlatform;
import com.solo.gb28181.request.GbPlatformPageRequest;
import com.solo.gb28181.response.GbPlatformVO;

import java.util.List;

/**
 * 下级级联平台服务（白名单管理）
 *
 * <p>下级平台 SIP 接入时视为设备，走现有设备注册流程；本服务只负责白名单的
 * 增删改查，以及注册鉴权时按 platformId 提供专属密码。</p>
 */
public interface GbPlatformService extends IService<GbPlatform> {

    /**
     * 分页查询白名单，并关联 gb_device（device_id == platform_id）回填在线状态等运行态字段
     */
    Page<GbPlatformVO> pageQuery(GbPlatformPageRequest request);

    /**
     * 后台录入白名单。platformId 20 位校验 + 唯一校验。
     */
    void createByManual(GbPlatform platform);

    /**
     * 后台编辑白名单。platformId 不可改。
     */
    void updateByManual(GbPlatform platform);

    /**
     * 批量删除白名单（仅删白名单记录，不动已注册的 gb_device 行）
     */
    void deleteBatch(List<Long> ids);

    /**
     * 按 platformId 查启用中的白名单；供 REGISTER 分流鉴权用。未命中或已禁用返回 null。
     */
    GbPlatform findEnabledByPlatformId(String platformId);
}