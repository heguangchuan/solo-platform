package com.solo.media.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.request.GbMediaServerPageRequest;
import com.solo.media.service.MediaServerLoader;
import com.solo.media.service.MediaServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流媒体节点服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServerServiceImpl extends ServiceImpl<GbMediaServerMapper, MediaServer>
        implements MediaServerService {

    private final MediaServerLoader loadIndex;

    @Override
    public Page<MediaServer> pageQuery(GbMediaServerPageRequest request) {
        LambdaQueryWrapper<MediaServer> wrapper = new LambdaQueryWrapper<>();
        if (request.getServerId() != null && !request.getServerId().isBlank()) {
            wrapper.like(MediaServer::getServerId, request.getServerId());
        }
        if (request.getStatus() != null) {
            wrapper.eq(MediaServer::getStatus, request.getStatus());
        }
        wrapper.orderByDesc(MediaServer::getUpdateTime);
        return page(new Page<>(request.getPage(), request.getPageSize()), wrapper);
    }

    @Override
    public void saveOrUpdateByServerId(MediaServer server) {
        MediaServer exist = lambdaQuery().eq(MediaServer::getServerId, server.getServerId()).one();
        if (exist == null) {
            save(server);
            log.info("[流媒体] 节点入库 serverId={} {}:{}", server.getServerId(), server.getHostIp(), server.getHttpPort());
        } else {
            server.setId(exist.getId());
            updateById(server);
        }
    }

    @Override
    public boolean removeById(Serializable id) {
        // 先查出 serverId，MySQL 删成功后再清 Redis 负载索引
        MediaServer s = getById(id);
        boolean ok = super.removeById(id);
        if (ok && s != null) {
            try {
                loadIndex.onNodeOffline(s.getServerId());
            } catch (Exception e) {
                // 负载索引不是配置权威源；清理失败不影响功能——下次选点会被
                // MediaServerSelector 走 status=1 兜底分支挡住。这里只记日志，不让 500。
                log.warn("[流媒体] 删除节点后清理 Redis 负载索引失败 serverId={} err={}",
                        s.getServerId(), e.getMessage());
            }
        }
        return ok;
    }

    @Override
    public void keepalive(String serverId) {
        // 直接走 update 一次性更新避免 select-then-update
        boolean ok = update(new LambdaUpdateWrapper<MediaServer>()
                .eq(MediaServer::getServerId, serverId)
                .set(MediaServer::getStatus, 1)
                .set(MediaServer::getHookAliveTime, LocalDateTime.now()));
        if (!ok) {
            log.warn("[ZLM 回调] on_server_keepalive 但节点未登记 serverId={}", serverId);
        }
    }
}
