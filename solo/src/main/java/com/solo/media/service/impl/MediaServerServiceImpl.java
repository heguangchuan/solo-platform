package com.solo.media.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.media.entity.MediaServer;
import com.solo.media.entity.StreamProxy;
import com.solo.media.entity.StreamPush;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.mapper.StreamProxyMapper;
import com.solo.media.mapper.StreamPushMapper;
import com.solo.media.request.GbMediaServerPageRequest;
import com.solo.media.response.MediaServerStreamStatsResponse;
import com.solo.media.service.MediaServerLoader;
import com.solo.media.service.MediaServerService;
import com.solo.onvif.entity.OnvifSession;
import com.solo.onvif.service.OnvifPlayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 流媒体节点服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServerServiceImpl extends ServiceImpl<GbMediaServerMapper, MediaServer>
        implements MediaServerService {

    private final MediaServerLoader loadIndex;
    private final StreamPushMapper streamPushMapper;
    private final StreamProxyMapper streamProxyMapper;
    private final GbInviteSessionService gbInviteSessionService;
    private final OnvifPlayService onvifPlayService;

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
    public MediaServerStreamStatsResponse getStreamStats(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            throw new ServiceException("serverId 不能为空");
        }
        MediaServer node = lambdaQuery().eq(MediaServer::getServerId, serverId).one();
        if (node == null) {
            throw new ServiceException("节点不存在 serverId=" + serverId);
        }

        MediaServerStreamStatsResponse out = new MediaServerStreamStatsResponse();
        out.setServerId(node.getServerId());
        out.setServerName(node.getName());
        out.setHostIp(node.getHostIp());
        out.setHttpPort(node.getHttpPort());
        out.setStatus(node.getStatus());

        // 国标会话：Redis 拉全量活跃会话后内存过滤。节点上的活跃点播/回放规模
        // 通常 0~N 路（远小于设备总量），比给 GbInviteSessionService 加索引更轻。
        List<GbInviteSession> gbSessions = Collections.emptyList();
        try {
            gbSessions = gbInviteSessionService.listAllActive().stream()
                    .filter(s -> serverId.equals(s.getServerId()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[流媒体] 节点流统计 gb 会话失败 serverId={} err={}", serverId, e.getMessage());
        }
        out.setGbSessions(gbSessions);
        out.setGbStreamCount((long) gbSessions.size());

        // 第三方推流：MySQL 一次按 mediaServerId 取
        List<StreamPush> pushes = Collections.emptyList();
        try {
            pushes = streamPushMapper.selectList(
                    new LambdaQueryWrapper<StreamPush>().eq(StreamPush::getMediaServerId, serverId));
        } catch (Exception e) {
            log.warn("[流媒体] 节点流统计 推流失败 serverId={} err={}", serverId, e.getMessage());
        }
        out.setPushes(pushes);
        out.setPushStreamCount((long) pushes.size());

        // 拉流代理：仅统计 status=1（实际在跑的）
        List<StreamProxy> proxies = Collections.emptyList();
        try {
            proxies = streamProxyMapper.selectList(
                    new LambdaQueryWrapper<StreamProxy>()
                            .eq(StreamProxy::getMediaServerId, serverId)
                            .eq(StreamProxy::getStatus, 1));
        } catch (Exception e) {
            log.warn("[流媒体] 节点流统计 拉流代理失败 serverId={} err={}", serverId, e.getMessage());
        }
        out.setProxies(proxies);
        out.setProxyStreamCount((long) proxies.size());

        // ONVIF 实时点播会话：Redis 拉全量活跃会话后内存过滤。与 gb 走同套路（listAllActive + filter），
        // 节点上 ONVIF 活跃会话规模一般 0~几路，加索引收益不大。
        List<OnvifSession> onvifSessions = Collections.emptyList();
        try {
            onvifSessions = onvifPlayService.listAllActive().stream()
                    .filter(s -> serverId.equals(s.getServerId()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[流媒体] 节点流统计 onvif 会话失败 serverId={} err={}", serverId, e.getMessage());
        }
        out.setOnvifSessions(onvifSessions);
        out.setOnvifStreamCount((long) onvifSessions.size());

        // 总数取 Redis ZSet 节点分数（ZLM on_stream_changed 统一维护，含 GB / 推流 / 拉流代理 / ONVIF / 任何其他来源），
        // 不要再用 gb+push+proxy+onvif 累加——会漏掉未来其他来源。Redis 不可达 / 节点未入索引时降级为 0。
        Double liveLoad = null;
        try {
            liveLoad = loadIndex.getLoad(serverId);
        } catch (Exception e) {
            log.warn("[流媒体] 节点流统计 读 ZSet 失败 serverId={} err={}", serverId, e.getMessage());
        }
        out.setTotal(liveLoad == null ? 0L : liveLoad.longValue());
        return out;
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
