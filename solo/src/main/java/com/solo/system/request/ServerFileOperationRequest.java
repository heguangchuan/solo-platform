package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "服务器文件操作请求（下载 / 删除）")
@Data
public class ServerFileOperationRequest {

    @Schema(description = "目标服务器 ID")
    private Long serverId;

    @Schema(description = "远端文件或目录的绝对路径")
    private String path;
}
