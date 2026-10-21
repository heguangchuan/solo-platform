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


public interface MediaServerService extends IService<MediaServer> {

    Page<MediaServer> pageQuery(GbMediaServerPageRequest request);

    StreamStatsSummaryResponse getStreamStatsSummary(String serverId);

    List<StreamStatsGbItem> getStreamStatsGbDetail(String serverId);

    List<StreamStatsPushItem> getStreamStatsPushDetail(String serverId);

    List<StreamStatsProxyItem> getStreamStatsProxyDetail(String serverId);

    List<StreamStatsOnvifItem> getStreamStatsOnvifDetail(String serverId);

    void saveOrUpdateByServerId(MediaServer server);

    void keepalive(String serverId);
}
