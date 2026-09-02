package com.solo.media.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点流统计 - 国标会话明细项
 *
 * <p>对应 {@code GET /api/media-server/{serverId}/stream-stats/gb} 列表元素。
 * 字段是管理台弹窗表格所需最小集（{@code streamId / deviceId / channelId / sessionType / status / startTime}），
 * 已剥离 GB 会话实体中前端用不到的协议字段（如 ssrc、Call-ID 头），减少响应体积。</p>
 *
 * <h3>{@link #sessionType} 取值</h3>
 * <ul>
 *   <li>{@code PLAY} — 实时点播</li>
 *   <li>{@code PLAYBACK} — 历史回放</li>
 *   <li>{@code DOWNLOAD} — 录像下载（一般不会在统计里出现，下完即结束）</li>
 * </ul>
 *
 * <h3>{@link #status} 取值与 UI 建议</h3>
 * <ul>
 *   <li>{@code INVITING} — INVITE 已发，流媒体服务器未确认流就绪；UI 建议黄色 tag + 转圈</li>
 *   <li>{@code READY} — 流就绪，{@link #startTime} 有值；UI 建议绿色 tag</li>
 *   <li>{@code STOPPED} / {@code TIMEOUT} / {@code FAILED} — 终态，列表里一般已清理</li>
 * </ul>
 */
@Data
public class StreamStatsGbItem {

    /** 业务主键：{@code <deviceId>_<channelId>}（回放再附时间段后缀），可用于断流接口的入参 */
    private String streamId;

    /** 设备国标 ID（20 位数字） */
    private String deviceId;

    /** 通道国标 ID（20 位数字） */
    private String channelId;

    /** 会话类型：{@code PLAY}（实时） / {@code PLAYBACK}（回放） / {@code DOWNLOAD}（下载） */
    private String sessionType;

    /** 会话状态：{@code INVITING} / {@code READY} / {@code STOPPED} / {@code TIMEOUT} / {@code FAILED} */
    private String status;

    /** 会话开始时间，{@code yyyy-MM-dd HH:mm:ss} 格式，表格直接展示 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
}
