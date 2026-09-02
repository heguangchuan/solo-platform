package com.solo.system.response;

import lombok.Data;

/**
 * 服务器文件响应
 *
 * <p>对应接口 {@code POST /api/server/file/list}，列出远端目录下的文件与子目录平铺列表。
 * 目录与文件以 {@link #directory} 区分；前端可按需渲染图标和右键菜单。</p>
 */
@Data
public class ServerFileResponse {

    /** 文件或目录名 */
    private String name;

    /** 远端路径（绝对路径） */
    private String path;

    /** 是否目录：{@code true}=目录、{@code false}=文件 */
    private Boolean directory;

    /** 文件大小（字节；目录时为空） */
    private Long size;

    /** 最后修改时间（字符串，由后端按 {@code yyyy-MM-dd HH:mm:ss} 输出） */
    private String modifiedTime;
}
