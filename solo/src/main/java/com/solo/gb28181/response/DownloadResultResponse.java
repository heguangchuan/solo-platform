package com.solo.gb28181.response;

import lombok.Data;

/**
 * 录像下载响应
 *
 * <p>对应 {@code POST /api/gb28181/download/start} 与 {@code GET /api/gb28181/download/{streamId}}。</p>
 *
 * <h3>状态机（按值取不同 UI）</h3>
 * <ul>
 *   <li>{@link #STATUS_INVITING} — 黄色加载中：已开始下载指令，设备未应答 / 流未就绪</li>
 *   <li>{@link #STATUS_RECORDING} — 蓝色录制中：流就绪，正在生成 MP4</li>
 *   <li>{@link #STATUS_READY} — 绿色就绪：文件已落盘，{@link #downloadUrl} 可用</li>
 *   <li>{@link #STATUS_FAILED} — 红色失败：详见 {@link #errorMsg}</li>
 *   <li>{@link #STATUS_TIMEOUT} — 红色超时未完成</li>
 * </ul>
 *
 * <p>任务仅在内存中按 streamId 索引，不入库——重启即丢，前端只能拿当前返回的状态，不能查历史。</p>
 */
@Data
public class DownloadResultResponse {

    public static final String STATUS_INVITING = "INVITING";
    public static final String STATUS_RECORDING = "RECORDING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_TIMEOUT = "TIMEOUT";

    /** 流 ID（与 start 响应的 streamId 一致；前端 GET /status 用它查任务） */
    private String streamId;

    /** 当前任务状态（取 {@link #STATUS_INVITING} / {@link #STATUS_RECORDING} / {@link #STATUS_READY} / {@link #STATUS_FAILED} / {@link #STATUS_TIMEOUT}） */
    private String status;

    /** 起始时间（Unix 秒） */
    private Long startEpochSec;

    /** 结束时间（Unix 秒） */
    private Long endEpochSec;

    /** 时段总长（秒） = end - start */
    private Long durationSec;

    /** 文件就绪后的下载 URL（仅 {@link #STATUS_READY} 状态有值；其他状态为空字符串） */
    private String downloadUrl;

    /** 失败原因（仅 {@link #STATUS_FAILED} / {@link #STATUS_TIMEOUT} 状态有值） */
    private String errorMsg;
}
