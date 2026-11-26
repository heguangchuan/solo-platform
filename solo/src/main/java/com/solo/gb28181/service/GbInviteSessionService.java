package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbInviteSession;

import java.util.List;

public interface GbInviteSessionService {

    void save(GbInviteSession session);

    void update(GbInviteSession session);

    void removeByStreamId(String streamId);

    GbInviteSession getByStreamId(String streamId);

    GbInviteSession getByCallId(String callId);

    GbInviteSession getBySsrc(String ssrc);

    List<GbInviteSession> listActiveByDeviceAndChannel(String deviceId, String channelId);

    List<GbInviteSession> listAllActive();
}
