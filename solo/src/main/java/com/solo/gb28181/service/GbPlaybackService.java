package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.request.PlaybackControlRequest;
import com.solo.gb28181.request.PlaybackStartRequest;
import com.solo.gb28181.request.PlaybackStopRequest;
import com.solo.gb28181.request.RecordInfoQueryRequest;
import com.solo.gb28181.response.PlaybackResultResponse;
import com.solo.gb28181.response.RecordItemResponse;
import com.solo.media.hook.ZlmHookService;

import java.util.List;

public interface GbPlaybackService {

    List<RecordItemResponse> queryRecordInfo(RecordInfoQueryRequest req);

    PlaybackResultResponse start(PlaybackStartRequest req);

    void stop(PlaybackStopRequest req);

    void control(PlaybackControlRequest req);


    void onRecordInfoReceived(String deviceId, int sn, List<RecordItemResponse> items);

    void onInviteOk(String callId, String remoteSsrc);

    void onStreamReady(String app, String stream, String mediaServerId);

    void onStreamStopped(String app, String stream, String mediaServerId);

    void onRtpServerTimeout(String streamId);

    void onDeviceBye(GbInviteSession session);
}
