package com.solo.media.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点流统计 - 第三方推流明细项
 *
 * <p>对应 {@code GET /api/media-server/{serverId}/stream-stats/push} 列表元素。
 * 字段为管理台弹窗表格所需最小集（{@code app / stream / originType / aliveSecond / pushTime}），
 * 不复用 {@code StreamPush} 实体以减少 JSON 体积。</p>
 *
 * <h3>{@link #originType} 取值</h3>
 * <ul>
 *   <li>{@code rtmp} — RTMP 推流</li>
 *   <li>{@code rtsp} — RTSP 推流</li>
 *   <li>{@code rtc} — WebRTC 推流</li>
 * </ul>
 */
@Data
public class StreamStatsPushItem {

    /** ZLM app 名（默认 {@code live}） */
    private String app;

    /** ZLM stream 名（推流方自定义） */
    private String stream;

    /** 推流类型：{@code rtmp} / {@code rtsp} / {@code rtc} */
    private String originType;

    /** 推流存活秒数（ZLM 上报） */
    private Integer aliveSecond;

    /** 推流开始时间 {@code yyyy-MM-dd HH:mm:ss} */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime pushTime;
}
