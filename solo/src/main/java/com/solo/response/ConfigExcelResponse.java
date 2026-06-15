package com.solo.response;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class ConfigExcelResponse {
    @ExcelProperty("参数ID")
    private Long id;

    @ExcelProperty("参数名称")
    private String configName;

    @ExcelProperty("参数键")
    private String configKey;

    @ExcelProperty("参数值")
    private String configValue;

    @ExcelProperty("是否内置")
    private String configType;

    @ExcelProperty("备注")
    private String remark;

    @ExcelProperty("创建时间")
    private String createTime;
}
