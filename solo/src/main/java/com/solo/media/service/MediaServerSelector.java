package com.solo.media.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.common.ServiceException;
import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;


@Slf4j
@Component
@RequiredArgsConstructor
public class MediaServerSelector {

    private final GbMediaServerMapper mediaServerMapper;
    private final MediaServerLoader loadIndex;

    public MediaServer pick() {
        // 1. 优先按负载索引选最闲的：Loader 只返 serverId，MySQL 查实体由本类做
        String leastId = loadIndex.pickLeastLoadedServerId();
        if (leastId != null) {
            MediaServer least = mediaServerMapper.selectOne(
                    new LambdaQueryWrapper<MediaServer>().eq(MediaServer::getServerId, leastId));
            if (least != null && least.getStatus() != null && least.getStatus() == 1) {
                return least;
            }
            // 索引指向的节点已不存在或已离线（可能刚被健康检测标记离线）→ 落到 tier 2/3 兜底
            if (least != null) {
                log.warn("[流媒体] 负载索引指向离线节点 serverId={}", leastId);
            } else {
                log.warn("[流媒体] 负载索引指向已不存在的节点 serverId={}", leastId);
            }
        }

        // 2. 索引为空（或指向孤儿）时兜底：按 status=1 选第一个
        List<MediaServer> all = mediaServerMapper.selectList(null);
        for (MediaServer s : all) {
            if (s.getStatus() != null && s.getStatus() == 1) {
                return s;
            }
        }
        // 3. 索引为空 + 没有 status=1 的：回退首个登记节点（保留旧"不让 500"语义）
        if (!all.isEmpty()) {
            log.warn("[流媒体] 负载索引为空且无 status=1 节点，回退首个登记节点 serverId={}",
                    all.getFirst().getServerId());
            return all.getFirst();
        }
        throw new ServiceException("无可用流媒体节点，请先在管理面登记 ZLM");
    }
}
