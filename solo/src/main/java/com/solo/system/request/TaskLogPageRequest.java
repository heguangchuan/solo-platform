package com.solo.system.request;

import lombok.Data;

/**
 * 任务执行日志分页查询请求
 *
 * <p>分页参数：{@code page} 从 1 开始，{@code pageSize} 默认 20。
 * 对应接口 {@code POST /api/task/log/page}。</p>
 */
@Data
public class TaskLogPageRequest {

    /** 当前页码（从 1 开始），缺省 1 */
    private Long page = 1L;

    /** 每页条数，缺省 20 */
    private Long pageSize = 20L;

    /** 按任务 ID 过滤（可选） */
    private Long taskId;

    /** 按任务名称过滤（可选） */
    private String taskName;

    /** 按任务编码过滤（可选） */
    private String taskCode;

    /** 按执行结果状态过滤（可选）：{@code 0=失败}、{@code 1=成功} */
    private Integer resultStatus;
}
