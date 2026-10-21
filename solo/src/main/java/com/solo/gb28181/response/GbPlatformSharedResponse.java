package com.solo.gb28181.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "上级平台已共享的 catalog item 节点 id 集合")
@Data
public class GbPlatformSharedResponse {

    @Schema(description = "已共享的 catalog item 节点 id：分组 g_xxx / 通道 c_xxx")
    private List<String> channelIds;
}
