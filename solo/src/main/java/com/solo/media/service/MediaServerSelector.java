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
 * <p>先取在线（status=1）节点，无在线时回退到首个登记节点；一个节点都没有就抛异常。
 * 供国标点播 / 回放、ONVIF 点播等所有需要 ZLM 节点的编排层共用，避免各自重复实现选点逻辑。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaServerSelector {

    private final MediaServerService mediaServerService;

    /**
     * 挑一个可用节点
     *
     * @throws ServiceException 无任何登记节点
     */
    public MediaServer pick() {
        List<MediaServer> all = mediaServerService.list();
        for (MediaServer s : all) {
            if (s.getStatus() != null && s.getStatus() == 1) {
                return s;
            }
        }
        if (!all.isEmpty()) {
            log.warn("[流媒体] 无在线节点，使用首个登记节点 serverId={}", all.getFirst().getServerId());
            return all.getFirst();
        }
        throw new ServiceException("无可用流媒体节点，请先在管理面登记 ZLM");
    }
}
