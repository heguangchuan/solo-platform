package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbMediaServer;
import com.solo.gb28181.request.GbMediaServerPageRequest;

/**
 * 流媒体节点服务
 *
 * <p>仅暴露"业务动作"。简单的按字段查询（如按 serverId 取一行）由调用方直接走
 * {@code lambdaQuery().eq(GbMediaServer::getServerId, ...).one()}，不在此处增加薄方法。</p>
 */
public interface GbMediaServerService extends IService<GbMediaServer> {

    Page<GbMediaServer> pageQuery(GbMediaServerPageRequest request);

    /** 保存或更新节点（按 serverId 唯一） */
    void saveOrUpdateByServerId(GbMediaServer server);

    /** 节点心跳：刷新 hookAliveTime + 置 status=1 */
    void keepalive(String serverId);
}
