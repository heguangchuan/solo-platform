package com.solo.system.response;

import lombok.Data;

/**
 * 系统统计信息响应体
 * 包含系统用户、角色、权限的总数
 */
@Data
public class StatResponse {

    /**
     * 系统用户总数
     */
    private Long userCount;

    /**
     * 系统角色总数
     */
    private Long roleCount;

    /**
     * 系统权限总数
     */
    private Long permissionCount;
}
