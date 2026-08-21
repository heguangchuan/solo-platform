package com.solo.gb28181.response;

import lombok.Data;

/**
 * 录像下载响应
 *
 * <h3>状态机</h3>
 * <ul>
 *   <li>{@link #STATUS_INVITING} — DOWNLOAD INVITE 已发，设备未应答 / 流未就绪</li>
 *   <li>{@link #STATUS_RECORDING} — 流就绪，ZLM 正在录 MP4</li>
 *   <li>{@link #STATUS_READY} — 文件已落 MinIO，{@link #downloadUrl} 可用</li>
 *   <li>{@link #STATUS_FAILED} — 失败，{@link #errorMsg} 有原因</li>
 *   <li>{@link #STATUS_TIMEOUT} — 超时未完成</li>
 * </ul>
 *
 * <p>任务记录仅存内存（{@code DownloadTaskRegistry}，按 streamId 索引），
 * 与"不入库"原则对齐——重启即丢，前端只能拿当前返回的状态，不能查历史。</p>
 */
@Data
public class DownloadResultResponse {

    public static final String STATUS_INVITING = "INVITING";
    public static final String STATUS_RECORDING = "RECORDING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_TIMEOUT = "TIMEOUT";

    /** ZLM 流名（与 INVITE 的 stream 段一致），前端 GET /status 用它查任务 */
    private String streamId;

    /** 当前任务状态 */
    private String status;

    /** 起始时间（Unix 秒） */
    private Long startEpochSec;

    /** 结束时间（Unix 秒） */
    private Long endEpochSec;

    /** 时段总长（秒） = end - start */
    private Long durationSec;

    /** 文件就绪后的 MinIO 预签名 URL（仅 READY 状态有值） */
    private String downloadUrl;

    /** 失败原因（仅 FAILED / TIMEOUT 状态有值） */
    private String errorMsg;
}
