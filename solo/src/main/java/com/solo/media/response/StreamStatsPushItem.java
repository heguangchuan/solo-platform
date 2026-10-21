package com.solo.media.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "节点流统计 - 第三方推流明细项")
@Data
public class StreamStatsPushItem {

    @Schema(description = "ZLM 应用名（默认 live）")
    private String app;

    @Schema(description = "ZLM 流名（推流方自定义的标识）")
    private String stream;

    @Schema(description = "推流协议：rtmp / rtsp / rtc")
    private String originType;

    @Schema(description = "推流存活秒数；前端可格式化为 Xh Ym Zs 形式展示")
    private Integer aliveSecond;

    @Schema(description = "推流开始时间，yyyy-MM-dd HH:mm:ss 格式")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime pushTime;
}
