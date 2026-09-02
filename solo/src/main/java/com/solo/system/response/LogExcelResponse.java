package com.solo.system.response;

import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 操作日志 Excel 导出响应
 *
 * <p>对应接口 {@code POST /api/log/export}，响应为 {@code .xlsx} 二进制流。
 * {@link #responseStatus} 在 Excel 中以"成功"/"失败"文本呈现；{@link #createTime} 为 {@code yyyy-MM-dd HH:mm:ss} 字符串。</p>
 */
@Schema(description = "操作日志 Excel 导出响应")
@Data
public class LogExcelResponse {

    /** 日志 ID */
    @Schema(description = "日志 ID")
    @ExcelProperty("日志ID")
    private Long id;

    /** 操作人员账号 */
    @Schema(description = "操作人员账号")
    @ExcelProperty("操作人员")
    private String operatorName;

    /** 业务模块 */
    @Schema(description = "业务模块")
    @ExcelProperty("业务模块")
    private String module;

    /** 业务类型 */
    @Schema(description = "业务类型")
    @ExcelProperty("业务类型")
    private String businessType;

    /** 操作描述 */
    @Schema(description = "操作描述")
    @ExcelProperty("操作描述")
    private String description;

    /** 请求方式（GET/POST/...） */
    @Schema(description = "请求方式")
    @ExcelProperty("请求方式")
    private String requestMethod;

    /** 请求地址 */
    @Schema(description = "请求地址")
    @ExcelProperty("请求地址")
    private String requestUrl;

    /** 响应状态（{@code 成功/失败} 文本） */
    @Schema(description = "响应状态")
    @ExcelProperty("响应状态")
    private String responseStatus;

    /** 响应耗时（毫秒） */
    @Schema(description = "响应耗时")
    @ExcelProperty("响应耗时")
    private Long costMs;

    /** 客户来源（{@code web} / {@code app}） */
    @Schema(description = "客户来源")
    @ExcelProperty("客户来源")
    private String clientSource;

    /** 客户 IP */
    @Schema(description = "客户 IP")
    @ExcelProperty("客户地址")
    private String clientIp;

    /** 错误信息（仅失败时） */
    @Schema(description = "错误信息")
    @ExcelProperty("错误信息")
    private String errorMsg;

    /** 操作时间（{@code yyyy-MM-dd HH:mm:ss}） */
    @Schema(description = "操作时间")
    @ExcelProperty("操作时间")
    private String createTime;
}
