package com.solo.system.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户状态统计 VO
 *
 * <p>对应接口 {@code GET /api/user/status-count}，全局口径，与列表查询条件无关。
 * 一条 GROUP BY SQL 同时返回启用/停用数量。</p>
 */
@Schema(description = "用户状态统计")
@Data
public class UserStatusCountVO {

    /** 用户状态：{@code 0=停用}、{@code 1=启用} */
    @Schema(description = "状态（0/1）")
    private Integer status;

    /** 该状态下的用户数 */
    @Schema(description = "用户数")
    private Long count;
}
