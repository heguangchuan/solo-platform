package com.solo.openapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.service.GbCloudRecordService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.openapi.response.OpenRecordStatusResponse;
import com.solo.openapi.service.OpenCatalogService;
import com.solo.openapi.service.OpenRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpenRecordServiceImpl implements OpenRecordService {

    private static final String APP = "rtp";

    private final GbCloudRecordService cloudRecordService;
    private final GbInviteSessionService inviteSessionService;
    private final OpenCatalogService catalogService;
    private final GbMediaServerMapper mediaServerMapper;
    private final ZlmRestClient zlmRestClient;

    @Override
    public String start(String deviceId, String channelId, String transport, String streamNumber) {
        GbDeviceChannel channel = catalogService.resolveChannel(deviceId, channelId);
        return cloudRecordService.startManual(
                channel.getDeviceId(), channel.getChannelId(), transport, streamNumber);
    }

    @Override
    public void stop(String streamId) {
        cloudRecordService.stop(streamId);
    }

    @Override
    public OpenRecordStatusResponse status(String streamId) {
        OpenRecordStatusResponse vo = new OpenRecordStatusResponse();
        vo.setStreamId(streamId);
        GbInviteSession session = inviteSessionService.getByStreamId(streamId);
        if (session == null) {
            vo.setStatus("NO_SESSION");
            return vo;
        }
        vo.setServerId(session.getServerId());
        MediaServer server = mediaServerMapper.selectOne(new LambdaQueryWrapper<MediaServer>()
                .eq(MediaServer::getServerId, session.getServerId()));
        if (server == null) {
            vo.setStatus("UNAVAILABLE");
            return vo;
        }
        ZlmRestClient.RecordState state = zlmRestClient.isRecording(server, APP, streamId);
        vo.setStatus(state.name());
        return vo;
    }
}
