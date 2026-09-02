package com.solo.system.request;

import lombok.Data;

/**
 * 服务器文件列表请求
 *
 * <p>对应接口 {@code POST /api/server/file/list}，列远端目录下文件 + 子目录。</p>
 */
@Data
public class ServerFileListRequest {

    /** 目标服务器 ID（必填，来自 {@code /api/server/page}） */
    private Long serverId;

    /** 远端目录绝对路径（必填，如 {@code "/var/log"}） */
    private String path;
}
