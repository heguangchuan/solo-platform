package com.solo.media.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点流统计 - ONVIF 实时点播会话明细项
 *
 * <p>对应 {@code GET /api/media-server/{serverId}/stream-stats/onvif} 列表元素。
 * 字段为管理台弹窗表格所需最小集（{@code streamId / deviceId / proxyKey / startTime}），
 * 不复用 {@code OnvifSession} 实体（其实 {@code OnvifSession} 字段也少，这里保持命名一致便于切换）。</p>
 */
@Data
public class StreamStatsOnvifItem {

    /** 业务主键：{@code onvif_<deviceId>} */
    private String streamId;

    /** ONVIF 设备主键（关联 {@code onvif_device.id}） */
    private Long deviceId;

    /** ZLM {@code addStreamProxy} 返回的 key，用于 {@code delStreamProxy} */
    private String proxyKey;

    /** 会话开始时间 {@code yyyy-MM-dd HH:mm:ss} */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
}
