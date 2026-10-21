package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "字典数据分页查询请求参数")
@Data
public class DictDataPageRequest {

    @Schema(description = "当前页码")
    private Long page = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    @Schema(description = "字典类型编码")
    private String dictType;

    @Schema(description = "字典项标签")
    private String dictLabel;

    @Schema(description = "状态（0/1）")
    private Integer status;
}
