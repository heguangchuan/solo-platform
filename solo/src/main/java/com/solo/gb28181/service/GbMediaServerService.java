package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbMediaServer;
import com.solo.gb28181.request.GbMediaServerPageRequest;

/**
 * 流媒体节点服务
 */
public interface GbMediaServerService extends IService<GbMediaServer> {

    Page<GbMediaServer> pageQuery(GbMediaServerPageRequest request);

    /**
     * 按 ZLM 节点 ID 取节点；ZLM hook 回调按此键查询。
     */
    GbMediaServer getByServerId(String serverId);

    /**
     * 保存或更新节点（按 serverId 唯一）
     */
    void saveOrUpdateByServerId(GbMediaServer server);

    /**
     * 节点心跳：刷新 hookAliveTime + 置 status=1
     */
    void keepalive(String serverId);
}
