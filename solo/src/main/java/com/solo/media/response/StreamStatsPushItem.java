package com.solo.media.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点流统计 - 第三方推流明细项
 *
 * <p>对应 {@code GET /api/media-server/{serverId}/stream-stats/push} 列表元素。
 * 字段为管理台弹窗表格所需最小集（{@code app / stream / originType / aliveSecond / pushTime}），
 * 已剥离 StreamPush 实体中前端用不到的内部字段，减少响应体积。</p>
 *
 * <h3>{@link #originType} 取值与 UI 建议</h3>
 * <ul>
 *   <li>{@code rtmp} — RTMP 推流，最常见的推流协议（OBS / 摄像机 SDK / 移动端推流 SDK）</li>
 *   <li>{@code rtsp} — RTSP 推流，常见于 IP 摄像头 / NVR</li>
 *   <li>{@code rtc} — WebRTC 推流，浏览器原生推流 / 互动直播</li>
 * </ul>
 * <p>前端可按 originType 给行首加不同颜色的小色块或图标。</p>
 */
@Data
public class StreamStatsPushItem {

    /** ZLM 应用名（默认 {@code live}） */
    private String app;

    /** ZLM 流名（推流方自定义的标识） */
    private String stream;

    /** 推流协议：{@code rtmp} / {@code rtsp} / {@code rtc} */
    private String originType;

    /** 推流存活秒数；前端可格式化为 {@code Xh Ym Zs} 形式展示 */
    private Integer aliveSecond;

    /** 推流开始时间，{@code yyyy-MM-dd HH:mm:ss} 格式 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime pushTime;
}
