package com.solo.openapi.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 应用分页查询入参，跟其他模块的分页接口一样走 POST body。
 */
@Schema(description = "开放API应用分页请求")
@Data
public class OpenAppPageRequest {

    @Schema(description = "页码，默认 1")
    @Min(value = 1, message = "page 最小为 1")
    private long page = 1;

    @Schema(description = "每页条数，默认 20，最大 200")
    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 200, message = "pageSize 最大为 200")
    private long pageSize = 20;

    @Schema(description = "关键词，匹配应用名/appKey/联系人")
    private String keyword;

    @Schema(description = "状态 0停用 1启用")
    private Integer status;
}
