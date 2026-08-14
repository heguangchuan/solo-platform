package com.solo.media.service;

import com.solo.common.ServiceException;
import com.solo.media.entity.MediaServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 流媒体节点选择器
 *
 * <p>选点优先级：</p>
 * <ol>
 *     <li>{@link MediaServerLoader#pickLeastLoaded()} —— 升序 ZSet 首成员 = 活跃流数最少</li>
 *     <li>兜底：按 {@code status=1} 选第一个登记行（Redis 抖动 / 索引空时）</li>
 *     <li>兜底：返回首个登记行（即使离线，至少不让点播接口 500）</li>
 *     <li>全无登记节点：抛 {@link ServiceException}</li>
 * </ol>
 *
 * <p>供国标点播 / 回放、ONVIF 点播、拉流代理等所有需要 ZLM 节点的编排层共用，
 * 避免各自重复实现选点逻辑。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaServerSelector {

    private final MediaServerService mediaServerService;
    private final MediaServerLoader loadIndex;

    /**
     * 挑一个可用节点
     *
     * @throws ServiceException 无任何登记节点
     */
    public MediaServer pick() {
        // 1. 优先按负载索引选最闲的
        MediaServer least = loadIndex.pickLeastLoaded();
        if (least != null) {
            return least;
        }

        // 2. 索引为空时兜底：按 status=1 选第一个
        List<MediaServer> all = mediaServerService.list();
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
