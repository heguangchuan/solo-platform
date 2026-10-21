package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "字典数据新增/编辑请求体")
@Data
public class DictDataRequest {

    @Schema(description = "字典类型编码")
    private String dictType;

    @Schema(description = "字典项显示标签")
    private String dictLabel;

    @Schema(description = "字典项实际取值")
    private String dictValue;

    @Schema(description = "排序号")
    private Integer sort;

    @Schema(description = "状态（0/1）")
    private Integer status;
}
