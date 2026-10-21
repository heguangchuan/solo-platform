package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.request.DownloadStartRequest;
import com.solo.gb28181.response.DownloadResultResponse;

/**
 * <h3>异步状态机</h3>
 * <pre>
 *  start() ──▶ INVITING ──ZLM 流就绪──▶ RECORDING ──on_record_mp4 钩子──▶ READY
 *                  │                        │                              │
 *                  └──── 任意阶段失败/超时 ──┴──────────────────────────▶  FAILED/TIMEOUT
 * </pre>
 *
 * <h3>与回放的关键差异</h3>
 * <ul>
 *   <li>下载同步 <b>不阻塞</b>，{@link #start} 立即返回带 streamId 的 {@code INVITING} 状态；
 *       前端用 {@link #getStatus} 轮询到 READY 即可取下载链接</li>
 *   <li>下载完的是<b>单个 MP4 文件</b>，落 MinIO 后生成预签名 URL，不再走 ZLM 播放地址</li>
 *   <li>下载会话的 SSRC 用回放段（{@code isPlayback=true}），与历史回放共享池</li>
 *   <li>下载任务<b>不入库</b>，与 S5 报警对齐，详见 {@link com.solo.gb28181.support.DownloadTaskRegistry}</li>
 * </ul>
 *
 * <h3>调用入口</h3>
 * <ul>
 *   <li>Controller：{@code POST /download/start} 调 {@link #start}</li>
 *   <li>Controller：{@code GET /download/{streamId}} 调 {@link #getStatus}</li>
 *   <li>Controller：{@code POST /download/{streamId}/stop} 调 {@link #stop}</li>
 * </ul>
 */
public interface GbDownloadService {

    DownloadResultResponse start(DownloadStartRequest req);

    DownloadResultResponse getStatus(String streamId);

    void stop(String streamId);

    // ============ ZLM Hook 回调（由 {@code ZlmHookService} 路由） ============

    void onStreamReady(String app, String stream, String mediaServerId);

    void onStreamStopped(String app, String stream, String mediaServerId);

    void onRecordMp4(String app, String stream, String url, long fileSize, double duration);

    void onRtpServerTimeout(String streamId);

    void onDeviceBye(GbInviteSession session);
}
