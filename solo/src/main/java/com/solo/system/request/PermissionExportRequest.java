package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "权限导出请求")
@Data
public class PermissionExportRequest {

    @Schema(description = "指定导出的权限 ID 集合")
    private List<Long> ids;

    @Schema(description = "权限名称")
    private String permissionName;

    @Schema(description = "权限编码")
    private String permissionCode;

    @Schema(description = "类型（1=菜单、2=按钮）")
    private Integer type;

    @Schema(description = "状态（0/1）")
    private Integer status;
}
