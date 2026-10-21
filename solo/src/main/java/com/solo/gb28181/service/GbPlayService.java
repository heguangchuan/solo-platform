package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.request.PlayStartRequest;
import com.solo.gb28181.request.PlayStopRequest;
import com.solo.gb28181.response.PlayResultResponse;

/**
 * <h3>职责</h3>
 * <ul>
 *     <li>承接 controller 调用：start / stop</li>
 *     <li>承接 ZLM hook 回调：流就绪 / 流断开 / RTP 端口超时 / 设备主动 BYE</li>
 *     <li>编排 SSRC 申请、RTP 端口分配、SIP INVITE/BYE、超时清扫</li>
 * </ul>
 *
 * <h3>S2.2 范围</h3>
 * <p>仅 PLAY（实时点播）；PLAYBACK/DOWNLOAD 留待后续 Sprint。截图由 jessibuca 自带，不在此服务。</p>
 */
public interface GbPlayService {

    PlayResultResponse start(PlayStartRequest req);

    void stop(PlayStopRequest req);

    // ============ 内部钩子（由 ZlmHookController / InviteResponseProcessor / ByeRequestProcessor 调用）============

    void onInviteOk(String callId, String remoteSsrc);

    void onStreamReady(String app, String stream, String mediaServerId);

    void onStreamStopped(String app, String stream, String mediaServerId);

    void onRtpServerTimeout(String streamId);

    void onDeviceBye(GbInviteSession session);

    void autoStartPlay(String deviceId, String channelId, String serverId);
}
