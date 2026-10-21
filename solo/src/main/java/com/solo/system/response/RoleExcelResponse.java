package com.solo.system.response;

import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "角色Excel导出响应")
@Data
public class RoleExcelResponse {

    @Schema(description = "角色 ID")
    @ExcelProperty("角色ID")
    private Long id;

    @Schema(description = "角色编码")
    @ExcelProperty("角色编码")
    private String roleCode;

    @Schema(description = "角色名称")
    @ExcelProperty("角色名称")
    private String roleName;

    @Schema(description = "角色描述")
    @ExcelProperty("角色描述")
    private String description;

    @Schema(description = "角色状态")
    @ExcelProperty("角色状态")
    private String status;

    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private String createTime;
}
