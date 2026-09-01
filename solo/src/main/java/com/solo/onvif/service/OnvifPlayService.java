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

    /**
     * 是否本服务负责的流——ZLM Hook 用，识别 ONVIF 实时点播流做生命周期清理。
     *
     * <p>判据：app=本服务的 {@link #APP} 且 stream 以 {@link #STREAM_ID_PREFIX} 开头。
     * 把判别收敛到服务内，Hook 不需要知道"什么算 ONVIF 流"的实现细节（app 名 / 前缀格式）。</p>
     *
     * @param app   ZLM {@code on_stream_changed} / {@code on_stream_none_reader} 回调中的 app 字段
     * @param stream 同上回调中的 stream 字段
     */
    boolean isOnvifStream(String app, String stream);

    /** 开始点播；幂等 —— 已经在推的直接返回原 URL */
    OnvifPlayResultResponse start(OnvifPlayStartRequest req);

    /** 停止点播（用户主动调 stop） */
    void stop(OnvifPlayStopRequest req);

    /**
     * 钩子驱动的会话清理（{@code on_stream_none_reader} / {@code on_stream_changed} 触发的
     * "无人观看 / 流已断"场景）。
     *
     * <p>幂等：Redis 无记录时直接返回；<b>不</b>再调 {@code zlmRestClient.delStreamProxy}，
     * 因为走到这一路时 ZLM 已经主动停了拉流（或根本就没拉起来），重复调用反而会拿到 404。</p>
     */
    void cleanupByStreamId(String streamId);

    /**
     * 列出全部活跃会话（启动清扫 / 跨模块统计用）。
     *
     * <p>与 {@code GbInviteSessionService.listAllActive()} 同形态：从 Redis 拉所有
     * {@code onvif:play:*} 键，反序列化 hash 为 {@link OnvifSession}，任一单条读失败
     * 跳过该条不抛——避免脏数据让上游整体失败。返回的列表允许为空。</p>
     */
    List<OnvifSession> listAllActive();

    /** ZLM app 名（ONVIF 拉流用 rtsp，与国标 rtp 区分） */
    String APP = "rtsp";

    /** streamId 前缀 {@code onvif_<deviceId>}，与国标 {@code <deviceId>_<channelId>} 形态错开 */
    String STREAM_ID_PREFIX = "onvif_";
}
