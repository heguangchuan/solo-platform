package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.request.PlayStartRequest;
import com.solo.gb28181.request.PlayStopRequest;
import com.solo.gb28181.response.PlayResultResponse;

public interface GbPlayService {

    PlayResultResponse start(PlayStartRequest req);

    void stop(PlayStopRequest req);


    void onInviteOk(String callId, String remoteSsrc);

    void onStreamReady(String app, String stream, String mediaServerId);

    void onStreamStopped(String app, String stream, String mediaServerId);

    void onRtpServerTimeout(String streamId);

    void onDeviceBye(GbInviteSession session);

    void autoStartPlay(String deviceId, String channelId, String serverId);
}
