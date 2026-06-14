package com.solo.response;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class RoleExcelResponse {
    @ExcelProperty("角色ID")
    private Long id;

    @ExcelProperty("角色编码")
    private String roleCode;

    @ExcelProperty("角色名称")
    private String roleName;

    @ExcelProperty("描述")
    private String description;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("创建时间")
    private String createTime;
}
