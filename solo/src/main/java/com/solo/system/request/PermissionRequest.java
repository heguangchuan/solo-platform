package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "权限新增/编辑请求体")
@Data
public class PermissionRequest {

    @Schema(description = "权限名称")
    private String permissionName;

    @Schema(description = "权限编码")
    private String permissionCode;

    @Schema(description = "URL 路径")
    private String url;

    @Schema(description = "权限类型（1=菜单、2=按钮）")
    private Integer type;

    @Schema(description = "父级权限 ID")
    private Long parentId;

    @Schema(description = "排序号")
    private Integer sort;

    @Schema(description = "图标标识")
    private String icon;

    @Schema(description = "状态（0/1）")
    private Integer status;
}
