package com.solo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.entity.SysServer;
import com.solo.request.ServerPageRequest;
import com.solo.request.ServerRequest;

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
