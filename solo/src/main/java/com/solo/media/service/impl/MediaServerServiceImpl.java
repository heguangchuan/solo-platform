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
import com.solo.media.response.StreamStatsGbItem;
import com.solo.media.response.StreamStatsOnvifItem;
import com.solo.media.response.StreamStatsProxyItem;
import com.solo.media.response.StreamStatsPushItem;
import com.solo.media.response.StreamStatsSummaryResponse;
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
    public StreamStatsSummaryResponse getStreamStatsSummary(String serverId) {
        MediaServer node = requireNode(serverId);

        StreamStatsSummaryResponse out = new StreamStatsSummaryResponse();
        out.setServerId(node.getServerId());
        out.setServerName(node.getName());
        out.setHostIp(node.getHostIp());
        out.setHttpPort(node.getHttpPort());
        out.setStatus(node.getStatus());

        // 4 类 count：单次访问只为 4 个 long 计数，不取明细。明细走懒加载接口。
        out.setGbStreamCount(safeCount(() -> gbInviteSessionService.listAllActive().stream()
                .filter(s -> serverId.equals(s.getServerId())).count(), "gb", serverId));
        out.setPushStreamCount(safeCount(() -> streamPushMapper.selectCount(
                new LambdaQueryWrapper<StreamPush>().eq(StreamPush::getMediaServerId, serverId)),
                "push", serverId));
        out.setProxyStreamCount(safeCount(() -> streamProxyMapper.selectCount(
                new LambdaQueryWrapper<StreamProxy>()
                        .eq(StreamProxy::getMediaServerId, serverId)
                        .eq(StreamProxy::getStatus, 1)),
                "proxy", serverId));
        out.setOnvifStreamCount(safeCount(() -> onvifPlayService.listAllActive().stream()
                .filter(s -> serverId.equals(s.getServerId())).count(), "onvif", serverId));

        // total 走 Redis ZSet（ZLM on_stream_changed 维护的活跃流数，权威值）。
        // 与四类累加不一定相等——见 StreamStatsSummaryResponse#total。
        Double liveLoad = null;
        try {
            liveLoad = loadIndex.getLoad(serverId);
        } catch (Exception e) {
            log.warn("[流媒体] 节点流统计 读 ZSet 失败 serverId={} 错误:{}", serverId, e.getMessage());
        }
        out.setTotal(liveLoad == null ? 0L : liveLoad.longValue());
        return out;
    }

    @Override
    public List<StreamStatsGbItem> getStreamStatsGbDetail(String serverId) {
        requireNode(serverId);
        try {
            return gbInviteSessionService.listAllActive().stream()
                    .filter(s -> serverId.equals(s.getServerId()))
                    .map(MediaServerServiceImpl::toGbItem)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[流媒体] 节点流统计 gb 会话失败 serverId={} 错误:{}", serverId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<StreamStatsPushItem> getStreamStatsPushDetail(String serverId) {
        requireNode(serverId);
        try {
            return streamPushMapper.selectList(
                    new LambdaQueryWrapper<StreamPush>().eq(StreamPush::getMediaServerId, serverId))
                    .stream()
                    .map(MediaServerServiceImpl::toPushItem)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[流媒体] 节点流统计 推流失败 serverId={} 错误:{}", serverId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<StreamStatsProxyItem> getStreamStatsProxyDetail(String serverId) {
        requireNode(serverId);
        try {
            return streamProxyMapper.selectList(
                    new LambdaQueryWrapper<StreamProxy>()
                            .eq(StreamProxy::getMediaServerId, serverId)
                            .eq(StreamProxy::getStatus, 1))
                    .stream()
                    .map(MediaServerServiceImpl::toProxyItem)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[流媒体] 节点流统计 拉流代理失败 serverId={} 错误:{}", serverId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<StreamStatsOnvifItem> getStreamStatsOnvifDetail(String serverId) {
        requireNode(serverId);
        try {
            return onvifPlayService.listAllActive().stream()
                    .filter(s -> serverId.equals(s.getServerId()))
                    .map(MediaServerServiceImpl::toOnvifItem)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[流媒体] 节点流统计 onvif 会话失败 serverId={} 错误:{}", serverId, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ----------------- 内部辅助 -----------------

    private MediaServer requireNode(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            throw new ServiceException("serverId 不能为空");
        }
        MediaServer node = lambdaQuery().eq(MediaServer::getServerId, serverId).one();
        if (node == null) {
            throw new ServiceException("节点不存在 serverId=" + serverId);
        }
        return node;
    }

    private long safeCount(java.util.function.Supplier<Long> supplier, String type, String serverId) {
        try {
            Long n = supplier.get();
            return n == null ? 0L : n;
        } catch (Exception e) {
            log.warn("[流媒体] 节点流统计 {} 计数失败 serverId={} 错误:{}", type, serverId, e.getMessage());
            return 0L;
        }
    }

    private static StreamStatsGbItem toGbItem(GbInviteSession s) {
        StreamStatsGbItem v = new StreamStatsGbItem();
        v.setStreamId(s.getStreamId());
        v.setDeviceId(s.getDeviceId());
        v.setChannelId(s.getChannelId());
        v.setSessionType(s.getSessionType());
        v.setStatus(s.getStatus());
        v.setStartTime(s.getStartTime());
        return v;
    }

    private static StreamStatsPushItem toPushItem(StreamPush p) {
        StreamStatsPushItem v = new StreamStatsPushItem();
        v.setApp(p.getApp());
        v.setStream(p.getStream());
        v.setOriginType(p.getOriginType());
        v.setAliveSecond(p.getAliveSecond());
        v.setPushTime(p.getPushTime());
        return v;
    }

    private static StreamStatsProxyItem toProxyItem(StreamProxy p) {
        StreamStatsProxyItem v = new StreamStatsProxyItem();
        v.setApp(p.getApp());
        v.setStream(p.getStream());
        v.setSrcUrl(p.getSrcUrl());
        v.setStatus(p.getStatus());
        return v;
    }

    private static StreamStatsOnvifItem toOnvifItem(OnvifSession s) {
        StreamStatsOnvifItem v = new StreamStatsOnvifItem();
        v.setStreamId(s.getStreamId());
        v.setDeviceId(s.getDeviceId());
        v.setProxyKey(s.getProxyKey());
        v.setStartTime(s.getStartTime());
        return v;
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
                log.warn("[流媒体] 删除节点后清理 Redis 负载索引失败 serverId={} 错误:{}",
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
