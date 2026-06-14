package com.solo.response;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class PermissionExcelResponse {
    @ExcelProperty("权限ID")
    private Long id;

    @ExcelProperty("权限名称")
    private String permissionName;

    @ExcelProperty("权限编码")
    private String permissionCode;

    @ExcelProperty("URL")
    private String url;

    @ExcelProperty("类型")
    private String type;

    @ExcelProperty("父级ID")
    private Long parentId;

    @ExcelProperty("排序")
    private Integer sort;

    @ExcelProperty("状态")
    private String status;
}
