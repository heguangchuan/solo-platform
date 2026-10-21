package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "文件管理分页查询请求参数")
@Data
public class FilePageRequest {

    @Schema(description = "当前页码")
    private Long page = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 10L;

    @Schema(description = "文件名")
    private String originalName;

    @Schema(description = "上传人账号")
    private String uploaderName;

    @Schema(description = "创建时间起")
    private String startTime;

    @Schema(description = "创建时间止")
    private String endTime;

    @Schema(description = "文件 ID 集合")
    private List<Long> ids;
}
