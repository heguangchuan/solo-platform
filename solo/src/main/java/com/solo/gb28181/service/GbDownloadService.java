package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.request.DownloadStartRequest;
import com.solo.gb28181.response.DownloadResultResponse;

/**
 * 录像下载服务（S3 录像下载阶段，Sprint 3）
 *
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

    /**
     * 发起录像下载（异步）。立即返回当前任务快照，前端轮询 {@link #getStatus} 等到 READY。
     */
    DownloadResultResponse start(DownloadStartRequest req);

    /**
     * 查任务状态。streamId 不存在（已超时清理 / 进程重启）时返回 {@code status=NOT_FOUND}。
     */
    DownloadResultResponse getStatus(String streamId);

    /**
     * 主动停止下载：发 BYE → 关 RTP 端口 → 释放 SSRC → 标 FAILED。
     */
    void stop(String streamId);

    // ============ ZLM Hook 回调（由 {@code ZlmHookService} 路由） ============

    /**
     * ZLM 流就绪（{@code on_stream_changed}）：开启 ZLM MP4 录制，状态 → RECORDING。
     */
    void onStreamReady(String app, String stream, String mediaServerId);

    /**
     * ZLM 流断开（{@code on_stream_changed}，regist=false）：通常是设备发完时间段 BYE 后 ZLM 关流。
     * 录像 MP4 会在 ZLM 完成切片后通过 {@link #onRecordMp4} 回调过来，本方法只做状态观察。
     */
    void onStreamStopped(String app, String stream, String mediaServerId);

    /**
     * ZLM 录像 MP4 完成（{@code on_record_mp4}）：下载文件到 MinIO，标 READY。
     *
     * @param app       应用名
     * @param stream    流名
     * @param url       ZLM 回调里给的 HTTP URL（{@code url} 字段）。若为 null/空，按 FAILED 处理
     * @param fileSize  文件字节数（ZLM 回调里给，便于日志）
     * @param duration  录像时长（秒，ZLM 回调里给）
     */
    void onRecordMp4(String app, String stream, String url, long fileSize, double duration);

    /**
     * RTP 端口超时（{@code on_rtp_server_timeout}）。
     */
    void onRtpServerTimeout(String streamId);

    /**
     * 设备主动 BYE：结束下载流程，未完成的文件标记 FAILED。
     */
    void onDeviceBye(GbInviteSession session);
}
