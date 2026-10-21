package com.solo.system.response;

import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "操作日志 Excel 导出响应")
@Data
public class LogExcelResponse {

    @Schema(description = "日志 ID")
    @ExcelProperty("日志ID")
    private Long id;

    @Schema(description = "操作人员账号")
    @ExcelProperty("操作人员")
    private String operatorName;

    @Schema(description = "业务模块")
    @ExcelProperty("业务模块")
    private String module;

    @Schema(description = "业务类型")
    @ExcelProperty("业务类型")
    private String businessType;

    @Schema(description = "操作描述")
    @ExcelProperty("操作描述")
    private String description;

    @Schema(description = "请求方式")
    @ExcelProperty("请求方式")
    private String requestMethod;

    @Schema(description = "请求地址")
    @ExcelProperty("请求地址")
    private String requestUrl;

    @Schema(description = "响应状态")
    @ExcelProperty("响应状态")
    private String responseStatus;

    @Schema(description = "响应耗时")
    @ExcelProperty("响应耗时")
    private Long costMs;

    @Schema(description = "客户来源")
    @ExcelProperty("客户来源")
    private String clientSource;

    @Schema(description = "客户 IP")
    @ExcelProperty("客户地址")
    private String clientIp;

    @Schema(description = "错误信息")
    @ExcelProperty("错误信息")
    private String errorMsg;

    @Schema(description = "操作时间")
    @ExcelProperty("操作时间")
    private String createTime;
}
