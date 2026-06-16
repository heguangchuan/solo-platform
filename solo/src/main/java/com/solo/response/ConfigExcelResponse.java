package com.solo.response;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class ConfigExcelResponse {
    @ExcelProperty("参数ID")
    private Long id;

    @ExcelProperty("参数名称")
    private String configName;

    @ExcelProperty("参数编码")
    private String configKey;

    @ExcelProperty("参数取值")
    private String configValue;

    @ExcelProperty("参数备注")
    private String remark;

    @ExcelProperty("创建时间")
    private String createTime;
}
