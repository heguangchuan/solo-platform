package com.solo.onvif.service;

import com.solo.onvif.request.OnvifPlayStartRequest;
import com.solo.onvif.request.OnvifPlayStopRequest;
import com.solo.onvif.response.OnvifPlayResultResponse;

/**
 * ONVIF 实时点播服务
 *
 * <p>比国标点播简单很多：不需要 SIP INVITE / SDP / SSRC / on_stream_changed 等待，
 * 直接告诉 ZLM 去 pull 一条 RTSP。</p>
 */
public interface OnvifPlayService {

    /** 开始点播；幂等 —— 已经在推的直接返回原 URL */
    OnvifPlayResultResponse start(OnvifPlayStartRequest req);

    /** 停止点播 */
    void stop(OnvifPlayStopRequest req);
}
