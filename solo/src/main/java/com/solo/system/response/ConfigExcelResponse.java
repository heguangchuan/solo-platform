package com.solo.system.response;

import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 系统参数 Excel 导出响应
 *
 * <p>对应接口 {@code POST /api/config/export}，响应为 {@code .xlsx} 二进制流。
 * 字段含义与 {@code /api/config/page} 一致；{@link #createTime} 为 {@code yyyy-MM-dd HH:mm:ss} 字符串。</p>
 */
@Schema(description = "系统参数 Excel 导出响应")
@Data
public class ConfigExcelResponse {

    /** 参数 ID */
    @Schema(description = "参数 ID")
    @ExcelProperty("参数ID")
    private Long id;

    /** 参数名称 */
    @Schema(description = "参数名称")
    @ExcelProperty("参数名称")
    private String configName;

    /** 参数编码 */
    @Schema(description = "参数编码")
    @ExcelProperty("参数编码")
    private String configKey;

    /** 参数取值 */
    @Schema(description = "参数取值")
    @ExcelProperty("参数取值")
    private String configValue;

    /** 参数备注 */
    @Schema(description = "参数备注")
    @ExcelProperty("参数备注")
    private String remark;

    /** 创建时间（{@code yyyy-MM-dd HH:mm:ss}） */
    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private String createTime;
}
