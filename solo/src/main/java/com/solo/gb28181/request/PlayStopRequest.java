package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 停止点播请求
 *
 * <p>对应 {@code POST /api/gb28181/play/stop}。streamId 不存在时按 404 处理。</p>
 */
@Schema(description = "停止点播请求")
@Data
public class PlayStopRequest {

    @Schema(description = "流 ID（约定 <deviceId>_<channelId>，点播 start 响应中返回）")
    private String streamId;
}
