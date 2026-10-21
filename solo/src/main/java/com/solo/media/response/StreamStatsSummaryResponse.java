package com.solo.media.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "节点流统计概要（弹窗首屏用）")
@Data
public class StreamStatsSummaryResponse {

    @Schema(description = "节点 serverId（管理台展示用）")
    private String serverId;

    @Schema(description = "节点显示名（管理台列表的同名字段）")
    private String serverName;

    @Schema(description = "流媒体服务器管理面 IP")
    private String hostIp;

    @Schema(description = "HTTP API 端口")
    private Integer httpPort;

    @Schema(description = "节点在线状态：0 离线，1 在线；前端可按值给 status 标签染色（绿 / 红）")
    private Integer status;

    @Schema(description = "国标点播 / 回放会话数")
    private Long gbStreamCount;

    @Schema(description = "第三方推流数")
    private Long pushStreamCount;

    @Schema(description = "拉流代理数（仅统计已启动的代理）")
    private Long proxyStreamCount;

    @Schema(description = "ONVIF 实时点播会话数")
    private Long onvifStreamCount;

    @Schema(description = "该节点上的活跃流总数（流媒体服务器视角）")
    private Long total;
}
