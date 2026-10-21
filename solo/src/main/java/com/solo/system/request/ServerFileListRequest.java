package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "服务器文件列表请求")
@Data
public class ServerFileListRequest {

    @Schema(description = "目标服务器 ID")
    private Long serverId;

    @Schema(description = "远端目录绝对路径")
    private String path;
}
