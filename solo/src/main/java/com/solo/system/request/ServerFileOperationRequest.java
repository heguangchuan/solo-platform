package com.solo.system.request;

import lombok.Data;

/**
 * 服务器文件操作请求（下载 / 删除）
 *
 * <p>对应接口 {@code POST /api/server/file/download}、{@code DELETE /api/server/file}。</p>
 */
@Data
public class ServerFileOperationRequest {

    /** 目标服务器 ID（必填） */
    private Long serverId;

    /** 远端文件或目录的绝对路径（必填） */
    private String path;
}
