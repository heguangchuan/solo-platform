package com.solo.gb28181.support;

import com.solo.media.entity.MediaServer;
import com.solo.gb28181.response.DownloadResultResponse;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * <h3>状态机</h3>
 * <pre>
 *  PENDING ──发 INVITE──▶ INVITING ──流就绪──▶ RECORDING ──文件落 S3 兼容存储──▶ READY
 *                            │                                              │
 *                            ▼                                              ▼
 *                          FAILED &lt;────────── 任意阶段失败 / 超时 ──────────  TIMEOUT
 * </pre>
 *
 * <p><b>为什么是内存对象</b>：与 S5 报警的"不入库"原则对齐，下载记录只在前端当前会话内有效。
 * 进程重启任务丢失，状态回查依赖 streamId 命中注册表 → 命中不到就是已过期/被清。</p>
 */
@Data
public class DownloadTask {

    /** ZLM 流名（与 INVITE SDP {@code a=stream} / MP4 文件名一致） */
    private String streamId;

    /** 设备国标 ID */
    private String deviceId;

    /** 通道国标 ID */
    private String channelId;

    /** 下载起始时间（原文）{@code yyyy-MM-dd HH:mm:ss} */
    private String startTime;

    /** 下载结束时间（原文） */
    private String endTime;

    /** 起始时间（Unix 秒，给前端做日历计算用） */
    private Long startEpochSec;

    /** 结束时间（Unix 秒） */
    private Long endEpochSec;

    /** 收流模式：{@code UDP} / {@code TCP-passive} / {@code TCP-active} */
    private String transport;

    /** 下载倍速：{@code 1/2/4/8} */
    private Integer speed;

    /** 码流 token：空/不传 = 主码流 */
    private String streamNumber;

    /** 选中的 ZLM 节点（INVITE 时选定，录完 MP4 要按这个节点查文件） */
    private MediaServer mediaServer;

    /** RTP 端口（来自 {@code ZlmRestClient.openRtpServer}） */
    private Integer rtpPort;

    /** 申请到的 SSRC（便于日志回溯） */
    private String ssrc;

    /** 当前状态：见 {@link DownloadResultResponse#STATUS_INVITING} 等常量 */
    private String status;

    /** 失败 / 超时原因（仅 FAILED / TIMEOUT 状态有值） */
    private String errorMsg;

    /** 落 S3 兼容存储后的对象名（{@code gb28181/download/<streamId>/<原始 mp4 名>}） */
    private String objectName;

    /** 落 S3 兼容存储后的预签名 URL（仅 READY 状态有值） */
    private String downloadUrl;

    /** 任务创建时间（业务起点：Controller 入参后） */
    private LocalDateTime createdAt;

    /** INVITE 发出时间（RECORDING 起点） */
    private LocalDateTime inviteAt;

    /** 文件就绪时间（READY 起点） */
    private LocalDateTime readyAt;
}
