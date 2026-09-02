package com.solo.system.response;

import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 权限 Excel 导出响应
 *
 * <p>对应接口 {@code POST /api/permission/export}，响应为 {@code .xlsx} 二进制流。
 * {@link #type} 在 Excel 中以"菜单/按钮/未知"文本呈现，{@link #status} 以"启用/禁用"文本呈现。</p>
 */
@Schema(description = "权限 Excel 导出响应")
@Data
public class PermissionExcelResponse {

    /** 权限 ID */
    @Schema(description = "权限 ID")
    @ExcelProperty("权限ID")
    private Long id;

    /** 权限名称 */
    @Schema(description = "权限名称")
    @ExcelProperty("权限名称")
    private String permissionName;

    /** 权限编码 */
    @Schema(description = "权限编码")
    @ExcelProperty("权限编码")
    private String permissionCode;

    /** URL */
    @Schema(description = "URL")
    @ExcelProperty("URL")
    private String url;

    /** 类型（{@code 菜单/按钮/未知} 文本） */
    @Schema(description = "类型")
    @ExcelProperty("类型")
    private String type;

    /** 父级 ID */
    @Schema(description = "父级 ID")
    @ExcelProperty("父级ID")
    private Long parentId;

    /** 排序 */
    @Schema(description = "排序")
    @ExcelProperty("排序")
    private Integer sort;

    /** 状态（{@code 启用/禁用} 文本） */
    @Schema(description = "状态")
    @ExcelProperty("状态")
    private String status;
}
