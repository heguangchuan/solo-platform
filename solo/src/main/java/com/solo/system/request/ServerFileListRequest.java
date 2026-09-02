package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 服务器文件列表请求
 *
 * <p>对应接口 {@code POST /api/server/file/list}，列远端目录下文件 + 子目录。</p>
 */
@Schema(description = "服务器文件列表请求")
@Data
public class ServerFileListRequest {

    /** 目标服务器 ID（必填，来自 {@code /api/server/page}） */
    @Schema(description = "目标服务器 ID")
    private Long serverId;

    /** 远端目录绝对路径（必填，如 {@code "/var/log"}） */
    @Schema(description = "远端目录绝对路径")
    private String path;
}
