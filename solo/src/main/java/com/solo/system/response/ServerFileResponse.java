package com.solo.system.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 服务器文件响应
 *
 * <p>对应接口 {@code POST /api/server/file/list}，列出远端目录下的文件与子目录平铺列表。
 * 目录与文件以 {@link #directory} 区分；前端可按需渲染图标和右键菜单。</p>
 */
@Schema(description = "服务器文件响应")
@Data
public class ServerFileResponse {

    /** 文件或目录名 */
    @Schema(description = "文件或目录名")
    private String name;

    /** 远端路径（绝对路径） */
    @Schema(description = "远端路径")
    private String path;

    /** 是否目录：{@code true}=目录、{@code false}=文件 */
    @Schema(description = "是否目录")
    private Boolean directory;

    /** 文件大小（字节；目录时为空） */
    @Schema(description = "文件大小")
    private Long size;

    /** 最后修改时间（字符串，由后端按 {@code yyyy-MM-dd HH:mm:ss} 输出） */
    @Schema(description = "最后修改时间")
    private String modifiedTime;
}
