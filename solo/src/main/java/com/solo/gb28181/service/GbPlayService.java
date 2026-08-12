package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.request.PlayStartRequest;
import com.solo.gb28181.request.PlayStopRequest;
import com.solo.gb28181.response.PlayResultResponse;

/**
 * 实时点播 / 回放 / 下载编排服务
 *
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

    /**
     * 开始点播；同步阻塞等待流就绪或超时。
     *
     * @return 已就绪的播放地址聚合
     */
    PlayResultResponse start(PlayStartRequest req);

    /**
     * 主动停止点播：发 BYE → closeRtpServer → 释放 SSRC → session=STOPPED
     */
    void stop(PlayStopRequest req);

    // ============ 内部钩子（由 ZlmHookController / InviteResponseProcessor / ByeRequestProcessor 调用）============

    /**
     * INVITE 收到 2xx 时由 {@link com.solo.gb28181.processor.response.InviteResponseProcessor} 调用。
     *
     * <p>处理远端 SDP 里 {@code y=} 与本平台分配的 SSRC 不一致的情况（下级级联平台普遍自报 SSRC）：
     * 本平台固定多端口收流、<b>不按 SSRC 过滤</b>，任意 SSRC 都能收下，因此这里只把会话记录里的
     * SSRC 同步成远端实际在用的值，保持库内与现网一致。取舍理由见 gb28181.md ADR-025。</p>
     *
     * @param callId    INVITE Dialog Call-Id（按它反查会话）
     * @param remoteSsrc 200 OK SDP 中 {@code y=} 行解析出的 SSRC；为 null 表示设备没回 y=
     */
    void onInviteOk(String callId, String remoteSsrc);

    /**
     * ZLM hook {@code on_stream_changed regist=true}：流就绪
     * <p>找到 INVITING 会话，置为 READY、回填播放 URL、完成等待的 future。</p>
     */
    void onStreamReady(String app, String stream, String mediaServerId);

    /**
     * ZLM hook {@code on_stream_changed regist=false}：流断开
     */
    void onStreamStopped(String app, String stream, String mediaServerId);

    /**
     * ZLM hook {@code on_rtp_server_timeout}：RTP 端口无人推流超时
     */
    void onRtpServerTimeout(String streamId);

    /**
     * 收到设备主动 BYE：清扫，不再发本端 BYE
     */
    void onDeviceBye(GbInviteSession session);

    /**
     * 自动起点播（供 autoApplyPlay 按需拉流调用，异步执行）
     * @param deviceId   设备国标ID
     * @param channelId  通道国标ID
     * @param serverId   媒体服务器ID
     */
    void autoStartPlay(String deviceId, String channelId, String serverId);
}

