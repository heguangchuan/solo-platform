package com.solo.onvif.service;

import com.solo.onvif.entity.OnvifSession;
import com.solo.onvif.request.OnvifPlayStartRequest;
import com.solo.onvif.request.OnvifPlayStopRequest;
import com.solo.onvif.response.OnvifPlayResultResponse;

import java.util.List;

/**
 * <p>比国标点播简单很多：不需要 SIP INVITE / SDP / SSRC / on_stream_changed 等待，
 * 直接告诉 ZLM 去 pull 一条 RTSP。</p>
 */
public interface OnvifPlayService {

    boolean isOnvifStream(String app, String stream);

    OnvifPlayResultResponse start(OnvifPlayStartRequest req);

    void stop(OnvifPlayStopRequest req);

    void cleanupByStreamId(String streamId);

    List<OnvifSession> listAllActive();

    /** ZLM app 名（ONVIF 拉流用 rtsp，与国标 rtp 区分） */
    String APP = "rtsp";

    /** streamId 前缀 {@code onvif_<deviceId>}，与国标 {@code <deviceId>_<channelId>} 形态错开 */
    String STREAM_ID_PREFIX = "onvif_";
}
