package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 任务分页查询请求
 *
 * <p>分页参数：{@code page} 从 1 开始，{@code pageSize} 默认 20。
 * 对应接口 {@code POST /api/task/page}。</p>
 */
@Schema(description = "任务分页查询请求")
@Data
public class TaskPageRequest {

    /** 当前页码（从 1 开始），缺省 1 */
    @Schema(description = "当前页码")
    private Long page = 1L;

    /** 每页条数，缺省 20 */
    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    /** 任务名称（可选，精确匹配） */
    @Schema(description = "任务名称")
    private String taskName;

    /** 任务编码（可选，精确匹配） */
    @Schema(description = "任务编码")
    private String taskCode;

    /** 状态（可选）：{@code 0=停用}、{@code 1=启用} */
    @Schema(description = "状态（0/1）")
    private Integer status;
}
