package com.solo.system.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 系统统计信息响应体
 *
 * <p>对应接口 {@code GET /api/auth/stats}。
 * 返回用户 / 角色 / 权限三类资源的总数；常用于首页卡片展示。
 * 调用需同时具备 {@code system:user:list} / {@code system:role:list} / {@code system:permission:list} 三个权限码，否则返回 403。</p>
 */
@Schema(description = "系统统计信息响应体")
@Data
public class StatResponse {

    /** 系统用户总数 */
    @Schema(description = "系统用户总数")
    private Long userCount;

    /** 系统角色总数 */
    @Schema(description = "系统角色总数")
    private Long roleCount;

    /** 系统权限总数 */
    @Schema(description = "系统权限总数")
    private Long permissionCount;
}
