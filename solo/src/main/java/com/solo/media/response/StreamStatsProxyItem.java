package com.solo.media.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "节点流统计 - 拉流代理明细项")
@Data
public class StreamStatsProxyItem {

    @Schema(description = "ZLM 应用名（默认 proxy，与国标 rtp、ONVIF rtsp 区分）")
    private String app;

    @Schema(description = "ZLM 流名")
    private String stream;

    @Schema(description = "源地址（rtsp:// / rtmp:// / http-flv）")
    private String srcUrl;

    @Schema(description = "运行状态：0 未启动 1 已启动（当前接口只返回 status=1）")
    private Integer status;
}
