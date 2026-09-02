package com.solo.system.request;

import lombok.Data;

/**
 * 任务新增/编辑请求
 *
 * <p>对应接口 {@code POST /api/task}、{@code PUT /api/task/{id}}。
 * 任务创建后默认停用，需在列表页"启用"才进入调度。</p>
 */
@Data
public class TaskRequest {

    /** 任务名称（必填） */
    private String taskName;

    /** 任务编码（必填，全局唯一） */
    private String taskCode;

    /** cron 表达式（必填，7 位 Quartz 风格） */
    private String cron;

    /** 任务执行器在注册表中的 bean 名称（必填；具体可用值以后端提供为准） */
    private String handlerName;

    /** 任务参数（可选，JSON 字符串，透传给 handler） */
    private String taskParams;

    /** 状态（可选）：{@code 0=停用}、{@code 1=启用} */
    private Integer status;

    /** 备注（可选） */
    private String remark;
}
