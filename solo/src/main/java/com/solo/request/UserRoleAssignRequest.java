package com.solo.request;

import lombok.Data;

import java.util.List;

/**
 * 用户授权（分配角色）请求
 */
@Data
public class UserRoleAssignRequest {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 角色ID列表
     */
    private List<Long> roleIds;
}
