package com.solo.system.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "用户认证响应体（菜单树节点）")
@Data
public class UserAuthResponse {

    @Schema(description = "节点 ID")
    private Long id;

    @Schema(description = "菜单名称")
    private String permissionName;

    @Schema(description = "图标标识")
    private String icon;

    @Schema(description = "父级 ID")
    private Long parentId;

    @Schema(description = "排序号")
    private Integer sort;

    @Schema(description = "URL 路径")
    private String url;

    @Schema(description = "子节点")
    private List<UserAuthResponse> children;
}
