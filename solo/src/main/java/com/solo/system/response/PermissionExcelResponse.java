package com.solo.system.response;

import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "权限 Excel 导出响应")
@Data
public class PermissionExcelResponse {

    @Schema(description = "权限 ID")
    @ExcelProperty("权限ID")
    private Long id;

    @Schema(description = "权限名称")
    @ExcelProperty("权限名称")
    private String permissionName;

    @Schema(description = "权限编码")
    @ExcelProperty("权限编码")
    private String permissionCode;

    @Schema(description = "URL")
    @ExcelProperty("URL")
    private String url;

    @Schema(description = "类型")
    @ExcelProperty("类型")
    private String type;

    @Schema(description = "父级 ID")
    @ExcelProperty("父级ID")
    private Long parentId;

    @Schema(description = "排序")
    @ExcelProperty("排序")
    private Integer sort;

    @Schema(description = "状态")
    @ExcelProperty("状态")
    private String status;
}
