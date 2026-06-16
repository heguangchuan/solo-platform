package com.solo.response;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class LogExcelResponse {
    @ExcelProperty("日志ID")
    private Long id;

    @ExcelProperty("操作人员")
    private String operatorName;

    @ExcelProperty("业务模块")
    private String module;

    @ExcelProperty("业务类型")
    private String businessType;

    @ExcelProperty("操作描述")
    private String description;

    @ExcelProperty("请求方式")
    private String requestMethod;

    @ExcelProperty("请求地址")
    private String requestUrl;

    @ExcelProperty("响应状态")
    private String responseStatus;

    @ExcelProperty("响应耗时")
    private Long costMs;

    @ExcelProperty("客户来源")
    private String clientSource;

    @ExcelProperty("客户地址")
    private String clientIp;

    @ExcelProperty("错误信息")
    private String errorMsg;

    @ExcelProperty("操作时间")
    private String createTime;
}
