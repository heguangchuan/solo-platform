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

    List<RecordItemResponse> queryRecordInfo(RecordInfoQueryRequest req);

    PlaybackResultResponse start(PlaybackStartRequest req);

    void stop(PlaybackStopRequest req);

    void control(PlaybackControlRequest req);

    // ============ Handler 回调 ============

    void onRecordInfoReceived(String deviceId, int sn, List<RecordItemResponse> items);

    void onInviteOk(String callId, String remoteSsrc);

    void onStreamReady(String app, String stream, String mediaServerId);

    void onStreamStopped(String app, String stream, String mediaServerId);

    void onRtpServerTimeout(String streamId);

    void onDeviceBye(GbInviteSession session);
}
