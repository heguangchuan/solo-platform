package com.solo.gb28181.response;

import com.solo.gb28181.entity.GbDeviceChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "通道分页响应项")
@Data
@EqualsAndHashCode(callSuper = true)
public class GbDeviceChannelResponse extends GbDeviceChannel {

    @Schema(description = "主码流云端录制会话 ID，未录制时为空")
    private String recordStreamId;

    @Schema(description = "主码流云端录制状态：INVITING 连接中 / READY 录制中")
    private String recordStatus;
}
