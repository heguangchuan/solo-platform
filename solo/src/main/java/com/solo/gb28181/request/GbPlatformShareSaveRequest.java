package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "上级平台共享资源保存请求")
@Data
public class GbPlatformShareSaveRequest {

    @Schema(description = "勾选的 catalog item 节点 id：分组 g_xxx / 通道 c_xxx")
    private List<String> channelIds;
}
