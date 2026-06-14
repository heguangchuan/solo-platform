package com.solo.response;

import lombok.Data;

import java.util.List;

/**
 * 用户认证响应体
 * 仅包含前端菜单/权限渲染所需的最少字段
 */
@Data
public class UserAuthResponse {

    private Long id;
    private String permissionName;
    private String icon;
    private Long parentId;
    private Integer sort;
    private String url;
    private List<UserAuthResponse> children;
}
