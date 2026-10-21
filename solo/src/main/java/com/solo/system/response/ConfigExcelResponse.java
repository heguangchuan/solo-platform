package com.solo.system.response;

import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "系统参数 Excel 导出响应")
@Data
public class ConfigExcelResponse {

    @Schema(description = "参数 ID")
    @ExcelProperty("参数ID")
    private Long id;

    @Schema(description = "参数名称")
    @ExcelProperty("参数名称")
    private String configName;

    @Schema(description = "参数编码")
    @ExcelProperty("参数编码")
    private String configKey;

    @Schema(description = "参数取值")
    @ExcelProperty("参数取值")
    private String configValue;

    @Schema(description = "参数备注")
    @ExcelProperty("参数备注")
    private String remark;

    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private String createTime;
}
