package com.solo.system.request;

import lombok.Data;

/**
 * 任务分页查询请求
 *
 * <p>分页参数：{@code page} 从 1 开始，{@code pageSize} 默认 20。
 * 对应接口 {@code POST /api/task/page}。</p>
 */
@Data
public class TaskPageRequest {

    /** 当前页码（从 1 开始），缺省 1 */
    private Long page = 1L;

    /** 每页条数，缺省 20 */
    private Long pageSize = 20L;

    /** 任务名称（可选，精确匹配） */
    private String taskName;

    /** 任务编码（可选，精确匹配） */
    private String taskCode;

    /** 状态（可选）：{@code 0=停用}、{@code 1=启用} */
    private Integer status;
}
