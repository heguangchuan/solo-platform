package com.solo.gb28181.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "共享目录树节点响应")
@Data
public class GbShareTreeNodeResponse {

    @Schema(description = "节点唯一键（前端 el-tree node-key）：设备 d_xxx / 分组 g_xxx / 通道 c_xxx")
    private String id;

    @Schema(description = "节点显示标签")
    private String label;

    @Schema(description = "节点类型：DEVICE 设备（枝干） / GROUP 分组 / CHANNEL 通道")
    private String type;

    @Schema(description = "子节点")
    private List<GbShareTreeNodeResponse> children;
}
