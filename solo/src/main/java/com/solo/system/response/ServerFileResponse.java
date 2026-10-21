package com.solo.system.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "服务器文件响应")
@Data
public class ServerFileResponse {

    @Schema(description = "文件或目录名")
    private String name;

    @Schema(description = "远端路径")
    private String path;

    @Schema(description = "是否目录")
    private Boolean directory;

    @Schema(description = "文件大小")
    private Long size;

    @Schema(description = "最后修改时间")
    private String modifiedTime;
}
