package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.gb28181.entity.GbMediaServer;
import com.solo.gb28181.mapper.GbMediaServerMapper;
import com.solo.gb28181.request.GbMediaServerPageRequest;
import com.solo.gb28181.service.GbMediaServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 流媒体节点服务实现
 */
@Slf4j
@Service
public class GbMediaServerServiceImpl extends ServiceImpl<GbMediaServerMapper, GbMediaServer>
        implements GbMediaServerService {

    @Override
    public Page<GbMediaServer> pageQuery(GbMediaServerPageRequest request) {
        LambdaQueryWrapper<GbMediaServer> wrapper = new LambdaQueryWrapper<>();
        if (request.getServerId() != null && !request.getServerId().isBlank()) {
            wrapper.like(GbMediaServer::getServerId, request.getServerId());
        }
        if (request.getStatus() != null) {
            wrapper.eq(GbMediaServer::getStatus, request.getStatus());
        }
        wrapper.orderByDesc(GbMediaServer::getUpdateTime);
        return page(new Page<>(request.getPage(), request.getPageSize()), wrapper);
    }

    @Override
    public void saveOrUpdateByServerId(GbMediaServer server) {
        GbMediaServer exist = lambdaQuery().eq(GbMediaServer::getServerId, server.getServerId()).one();
        if (exist == null) {
            save(server);
            log.info("[流媒体] 节点入库 serverId={} {}:{}", server.getServerId(), server.getHostIp(), server.getHttpPort());
        } else {
            server.setId(exist.getId());
            updateById(server);
        }
    }

    @Override
    public void keepalive(String serverId) {
        // 直接走 update 一次性更新避免 select-then-update
        boolean ok = update(new LambdaUpdateWrapper<GbMediaServer>()
                .eq(GbMediaServer::getServerId, serverId)
                .set(GbMediaServer::getStatus, 1)
                .set(GbMediaServer::getHookAliveTime, LocalDateTime.now()));
        if (!ok) {
            log.warn("[ZLM 回调] on_server_keepalive 但节点未登记 serverId={}", serverId);
        }
    }
}
