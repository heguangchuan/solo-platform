package com.solo.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.system.entity.SysServer;
import com.solo.system.request.ServerPageRequest;
import com.solo.system.request.ServerRequest;

import java.util.List;

/**
 * 服务器服务接口
 */
public interface SysServerService extends IService<SysServer> {

    Page<SysServer> pageQuery(ServerPageRequest req);

    void createServer(ServerRequest request);

    void updateServer(Long id, ServerRequest request);

    void deleteServer(Long id);

    void deleteBatch(List<Long> ids);
}
