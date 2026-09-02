package com.solo.media.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.media.entity.MediaServer;
import com.solo.media.request.GbMediaServerPageRequest;
import com.solo.media.response.StreamStatsGbItem;
import com.solo.media.response.StreamStatsOnvifItem;
import com.solo.media.response.StreamStatsProxyItem;
import com.solo.media.response.StreamStatsPushItem;
import com.solo.media.response.StreamStatsSummaryResponse;

import java.util.List;

/**
 * 流媒体节点服务
 *
 * <p>仅暴露"业务动作"。简单的按字段查询（如按 serverId 取一行）由调用方直接走
 * {@code lambdaQuery().eq(GbMediaServer::getServerId, ...).one()}，不在此处增加薄方法。</p>
 */
public interface MediaServerService extends IService<MediaServer> {

    Page<MediaServer> pageQuery(GbMediaServerPageRequest request);

    /**
     * 取某节点的流统计 KPI 概要（弹窗首屏用）
     * <p>只返回节点身份 + 4 类会话的 count + 来自 ZLM hook 维护的 total。
     * 节点不存在时抛 {@code ServiceException}。</p>
     */
    StreamStatsSummaryResponse getStreamStatsSummary(String serverId);

    /**
     * 取某节点上国标点播 / 回放会话明细
     * <p>数据源：{@code Redis} 实时会话集（{@code GbInviteSessionService.listAllActive()}）按 serverId 内存过滤。
     * 节点不存在时抛 {@code ServiceException}，底层异常被吞并降级为空列表。</p>
     */
    List<StreamStatsGbItem> getStreamStatsGbDetail(String serverId);

    /**
     * 取某节点上第三方推流明细
     * <p>数据源：MySQL {@code stream_push} 按 {@code mediaServerId} 过滤。
     * 节点不存在时抛 {@code ServiceException}，底层异常被吞并降级为空列表。</p>
     */
    List<StreamStatsPushItem> getStreamStatsPushDetail(String serverId);

    /**
     * 取某节点上拉流代理明细
     * <p>数据源：MySQL {@code stream_proxy} 按 {@code mediaServerId} 过滤，且只取 {@code status=1}（已启动）。
     * 节点不存在时抛 {@code ServiceException}，底层异常被吞并降级为空列表。</p>
     */
    List<StreamStatsProxyItem> getStreamStatsProxyDetail(String serverId);

    /**
     * 取某节点上 ONVIF 实时点播会话明细
     * <p>数据源：{@code Redis} 实时会话集（{@code OnvifPlayService.listAllActive()}）按 serverId 内存过滤。
     * 节点不存在时抛 {@code ServiceException}，底层异常被吞并降级为空列表。</p>
     */
    List<StreamStatsOnvifItem> getStreamStatsOnvifDetail(String serverId);

    /** 保存或更新节点（按 serverId 唯一） */
    void saveOrUpdateByServerId(MediaServer server);

    /** 节点心跳：刷新 hookAliveTime + 置 status=1 */
    void keepalive(String serverId);
}
