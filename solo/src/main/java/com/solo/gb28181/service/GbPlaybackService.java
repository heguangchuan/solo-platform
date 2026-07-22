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

/**
 * 历史回放编排服务
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>承接 controller 调用：录像查询 / 开始回放 / 停止回放 / 回放控制</li>
 *   <li>承接 {@code RecordInfoResponseMessageHandler} 的分片合并回调（{@link #onRecordInfoReceived}）</li>
 *   <li>跟 {@link GbPlayService} 共享底层会话清扫机制（同样的 ZLM hook 钩子 — 通过会话表的 sessionType 区分）</li>
 * </ul>
 *
 * <h3>S3 范围</h3>
 * <p>RecordInfo Query + Playback INVITE + SIP INFO 控制；不含 Download，留给后续 Sprint。</p>
 */
public interface GbPlaybackService {

    /**
     * 录像查询（同步阻塞等待分片合并完成或超时）
     *
     * @return 录像段列表，按 startTime 升序
     */
    List<RecordItemResponse> queryRecordInfo(RecordInfoQueryRequest req);

    /**
     * 开始回放（同步阻塞等待流就绪或超时）
     */
    PlaybackResultResponse start(PlaybackStartRequest req);

    /**
     * 主动停止回放：发 BYE → closeRtpServer → 释放 SSRC → 删 session
     */
    void stop(PlaybackStopRequest req);

    /**
     * 回放控制（PAUSE / PLAY / SEEK / SCALE）— 走 SIP INFO + MANSRTSP body
     */
    void control(PlaybackControlRequest req);

    // ============ Handler 回调 ============

    /**
     * RecordInfo 分片合并完成时由 {@code RecordInfoResponseMessageHandler} 调用，
     * 唤醒 {@link #queryRecordInfo} 的等待 future。
     */
    void onRecordInfoReceived(String deviceId, int sn, List<RecordItemResponse> items);

    /**
     * INVITE 收到 2xx 时由 {@code InviteResponseProcessor} 调用（按 sessionType 路由到这里）。
     * <p>语义同 {@link GbPlayService#onInviteOk}：设备返回的 SSRC 与本地不一致时
     * 调 ZLM {@code updateRtpServerSSRC} 自适应，失败则发 BYE 主动放弃。</p>
     */
    void onInviteOk(String callId, String remoteSsrc);

    /**
     * ZLM hook 流就绪（回放流）。本接口与 {@link GbPlayService#onStreamReady} 是互补的：
     * <ul>
     *   <li>实时点播流（sessionType=PLAY）→ {@link GbPlayService#onStreamReady}</li>
     *   <li>历史回放流（sessionType=PLAYBACK）→ 本方法</li>
     * </ul>
     * 路由判断在 {@link ZlmHookService} 里按 streamId 反查 session 的 sessionType。
     */
    void onStreamReady(String app, String stream, String mediaServerId);

    /**
     * ZLM hook 流断开（回放流）
     */
    void onStreamStopped(String app, String stream, String mediaServerId);

    /**
     * ZLM hook {@code on_rtp_server_timeout}（回放流）
     */
    void onRtpServerTimeout(String streamId);

    /**
     * 收到设备主动 BYE（回放）
     */
    void onDeviceBye(GbInviteSession session);
}
