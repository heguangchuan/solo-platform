package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "文件元数据登记请求")
@Data
public class FileSaveRequest {

    @Schema(description = "原始文件名")
    private String originalName;

    @Schema(description = "存储文件名")
    private String storageName;

    @Schema(description = "可访问 URL")
    private String url;

    @Schema(description = "扩展名")
    private String ext;

    @Schema(description = "MIME 类型")
    private String mimeType;

    @Schema(description = "文件大小")
    private Long size;
}
