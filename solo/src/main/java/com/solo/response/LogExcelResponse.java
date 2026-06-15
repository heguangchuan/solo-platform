package com.solo.response;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class LogExcelResponse {
    @ExcelProperty("日志ID")
    private Long id;

    @ExcelProperty("操作人")
    private String operatorName;

    @ExcelProperty("模块")
    private String module;

    @ExcelProperty("业务类型")
    private String businessType;

    @ExcelProperty("操作描述")
    private String description;

    @ExcelProperty("请求方法")
    private String requestMethod;

    @ExcelProperty("请求URL")
    private String requestUrl;

    @ExcelProperty("状态")
    private String responseStatus;

    @ExcelProperty("耗时(ms)")
    private Long costMs;

    @ExcelProperty("客户端来源")
    private String clientSource;

    @ExcelProperty("客户端IP")
    private String clientIp;

    @ExcelProperty("错误信息")
    private String errorMsg;

    @ExcelProperty("操作时间")
    private String createTime;
}
