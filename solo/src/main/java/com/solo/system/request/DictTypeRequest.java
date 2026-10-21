package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "字典类型新增/编辑请求体")
@Data
public class DictTypeRequest {

    @Schema(description = "字典类型名称")
    private String dictName;

    @Schema(description = "字典类型编码")
    private String dictType;

    @Schema(description = "状态（0/1）")
    private Integer status;
}
