package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.request.GbParentPlatformPageRequest;

import java.util.List;

/**
 * 上级平台服务
 *
 * <p>管理本平台作为下级向上级平台注册的配置信息。
 * 后续 S6.b（级联下级侧）会用本服务的数据发起 SIP REGISTER。</p>
 */
public interface GbParentPlatformService extends IService<GbParentPlatform> {

    /**
     * 分页查询上级平台配置
     */
    Page<GbParentPlatform> pageQuery(GbParentPlatformPageRequest request);

    /**
     * 新增上级平台配置。serverGbId 20 位校验 + 唯一校验。
     */
    void create(GbParentPlatform entity);

    /**
     * 编辑上级平台配置。
     */
    void updatePlatform(GbParentPlatform entity);

    /**
     * 批量删除上级平台配置
     */
    void deleteBatch(List<Long> ids);
}
