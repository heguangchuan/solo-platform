package com.solo.system.response;

import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 角色 Excel 导出响应
 *
 * <p>对应接口 {@code POST /api/role/export}，响应为 {@code .xlsx} 二进制流。
 * {@link #status} 在 Excel 中以"启用/禁用"文本呈现；{@link #createTime} 为 {@code yyyy-MM-dd HH:mm:ss} 字符串。</p>
 */
@Schema(description = "角色 Excel 导出响应")
@Data
public class RoleExcelResponse {

    /** 角色 ID */
    @Schema(description = "角色 ID")
    @ExcelProperty("角色ID")
    private Long id;

    /** 角色编码 */
    @Schema(description = "角色编码")
    @ExcelProperty("角色编码")
    private String roleCode;

    /** 角色名称 */
    @Schema(description = "角色名称")
    @ExcelProperty("角色名称")
    private String roleName;

    /** 角色描述 */
    @Schema(description = "角色描述")
    @ExcelProperty("角色描述")
    private String description;

    /** 角色状态（{@code 启用/禁用} 文本） */
    @Schema(description = "角色状态")
    @ExcelProperty("角色状态")
    private String status;

    /** 创建时间（{@code yyyy-MM-dd HH:mm:ss}） */
    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private String createTime;
}
