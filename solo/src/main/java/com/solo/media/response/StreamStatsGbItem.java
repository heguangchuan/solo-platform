package com.solo.media.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点流统计 - 国标会话明细项
 *
 * <p>对应 {@code GET /api/media-server/{serverId}/stream-stats/gb} 列表元素。
 * 字段是管理台弹窗表格所需最小集（{@code streamId / deviceId / channelId / sessionType / status / startTime}），
 * 不复用 {@code GbInviteSession} 实体以减少 JSON 体积（实体里的 {@code flvUrl / ssrc / callId / fromTag / toTag}
 * 等管理台表格用不到）。</p>
 *
 * <h3>{@link #sessionType} 取值</h3>
 * <ul>
 *   <li>{@code PLAY} — 实时点播</li>
 *   <li>{@code PLAYBACK} — 历史回放</li>
 *   <li>{@code DOWNLOAD} — 录像下载（一般不会在统计里出现，下完即结束）</li>
 * </ul>
 *
 * <h3>{@link #status} 取值</h3>
 * <ul>
 *   <li>{@code INVITING} — INVITE 已发，ZLM 未确认流就绪</li>
 *   <li>{@code READY} — 流就绪，{@link #startTime} 有值</li>
 *   <li>{@code STOPPED} / {@code TIMEOUT} / {@code FAILED} — 终态，列表里一般已清</li>
 * </ul>
 */
@Data
public class StreamStatsGbItem {

    /** 业务主键：{@code <deviceId>_<channelId>}（回放再附时间段后缀） */
    private String streamId;

    /** 设备国标 ID（20 位） */
    private String deviceId;

    /** 通道国标 ID（20 位） */
    private String channelId;

    /** 会话类型：{@code PLAY} / {@code PLAYBACK} / {@code DOWNLOAD} */
    private String sessionType;

    /** 会话状态：{@code INVITING} / {@code READY} / {@code STOPPED} / {@code TIMEOUT} / {@code FAILED} */
    private String status;

    /** 会话开始时间 {@code yyyy-MM-dd HH:mm:ss} */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
}
