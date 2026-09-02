package com.solo.media.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点流统计 - ONVIF 实时点播会话明细项
 *
 * <p>对应 {@code GET /api/media-server/{serverId}/stream-stats/onvif} 列表元素。
 * 字段为管理台弹窗表格所需最小集（{@code streamId / deviceId / proxyKey / startTime}），
 * 命名风格与国标明细项对齐，便于前端表格组件复用。</p>
 */
@Schema(description = "节点流统计 - ONVIF 实时点播会话明细项")
@Data
public class StreamStatsOnvifItem {

    @Schema(description = "业务主键：onvif_<deviceId>；前端可调取流地址 / 断流时用")
    private String streamId;

    @Schema(description = "ONVIF 设备主键 ID")
    private Long deviceId;

    @Schema(description = "ZLM addStreamProxy 返回的 key；前端一般不用")
    private String proxyKey;

    @Schema(description = "会话开始时间，yyyy-MM-dd HH:mm:ss 格式")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
}
