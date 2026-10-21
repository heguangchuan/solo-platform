package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.request.GbParentPlatformPageRequest;

import java.util.List;

/**
 * <p>管理本平台作为下级向上级平台注册的配置信息。
 * 后续 S6.b（级联下级侧）会用本服务的数据发起 SIP REGISTER。</p>
 */
public interface GbParentPlatformService extends IService<GbParentPlatform> {

    Page<GbParentPlatform> pageQuery(GbParentPlatformPageRequest request);

    void create(GbParentPlatform entity);

    void updatePlatform(GbParentPlatform entity);

    void deleteBatch(List<Long> ids);
}
