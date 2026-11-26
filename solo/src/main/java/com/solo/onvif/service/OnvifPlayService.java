package com.solo.onvif.service;

import com.solo.onvif.entity.OnvifSession;
import com.solo.onvif.request.OnvifPlayStartRequest;
import com.solo.onvif.request.OnvifPlayStopRequest;
import com.solo.onvif.response.OnvifPlayResultResponse;

import java.util.List;

public interface OnvifPlayService {

    boolean isOnvifStream(String app, String stream);

    OnvifPlayResultResponse start(OnvifPlayStartRequest req);

    void stop(OnvifPlayStopRequest req);

    void cleanupByStreamId(String streamId);

    List<OnvifSession> listAllActive();

    // ONVIF 用 rtsp app 和 onvif_ 流名，跟国标 rtp/device_channel 错开。
    String APP = "rtsp";

    String STREAM_ID_PREFIX = "onvif_";
}
