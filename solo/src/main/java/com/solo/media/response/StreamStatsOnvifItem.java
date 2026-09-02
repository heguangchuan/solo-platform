package com.solo.media.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点流统计 - ONVIF 实时点播会话明细项
 *
 * <p>对应 {@code GET /api/media-server/{serverId}/stream-stats/onvif} 列表元素。
 * 字段为管理台弹窗表格所需最小集（{@code streamId / deviceId / proxyKey / startTime}），
 * 命名风格与国标明细项对齐，便于前端表格组件复用。</p>
 */
@Data
public class StreamStatsOnvifItem {

    /** 业务主键：{@code onvif_<deviceId>}；前端可调取流地址 / 断流时用 */
    private String streamId;

    /** ONVIF 设备主键（对应 {@code onvif_device.id}） */
    private Long deviceId;

    /** 流媒体服务器代理标识（内部用，前端一般不展示） */
    private String proxyKey;

    /** 会话开始时间，{@code yyyy-MM-dd HH:mm:ss} 格式 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
}
