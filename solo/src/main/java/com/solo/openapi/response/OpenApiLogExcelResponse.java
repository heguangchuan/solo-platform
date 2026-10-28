package com.solo.openapi.response;

import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "开放API调用日志 Excel 导出响应")
@Data
public class OpenApiLogExcelResponse {

    @ExcelProperty("调用时间")
    private String createTime;

    @ExcelProperty("应用 appKey")
    private String appKey;

    @ExcelProperty("请求ID")
    private String requestId;

    @ExcelProperty("请求方法")
    private String method;

    @ExcelProperty("请求路径")
    private String path;

    @ExcelProperty("来源IP")
    private String clientIp;

    @ExcelProperty("HTTP状态")
    private Integer httpStatus;

    @ExcelProperty("业务码")
    private Integer bizCode;

    @ExcelProperty("调用结果")
    private String result;

    @ExcelProperty("耗时(ms)")
    private Long costMs;

    @ExcelProperty("错误信息")
    private String errorMsg;
}
