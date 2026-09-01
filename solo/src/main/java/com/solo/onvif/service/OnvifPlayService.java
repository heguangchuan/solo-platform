package com.solo.onvif.service;

import com.solo.onvif.entity.OnvifSession;
import com.solo.onvif.request.OnvifPlayStartRequest;
import com.solo.onvif.request.OnvifPlayStopRequest;
import com.solo.onvif.response.OnvifPlayResultResponse;

import java.util.List;

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

    /**
     * 列出全部活跃会话（启动清扫 / 跨模块统计用）。
     *
     * <p>与 {@code GbInviteSessionService.listAllActive()} 同形态：从 Redis 拉所有
     * {@code onvif:play:*} 键，反序列化 hash 为 {@link OnvifSession}，任一单条读失败
     * 跳过该条不抛——避免脏数据让上游整体失败。返回的列表允许为空。</p>
     */
    List<OnvifSession> listAllActive();
}
