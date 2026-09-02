package com.solo.system.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志分页查询请求
 *
 * <p>分页参数：{@code page} 从 1 开始，{@code pageSize} 默认 20。
 * 支持按操作人 / 模块 / 业务类型 / 响应状态 / 客户来源 / 时间范围过滤。
 * 对应接口 {@code POST /api/log/page}。</p>
 */
@Data
public class LogPageRequest {

    /** 当前页码（从 1 开始），缺省 1 */
    private Long page = 1L;

    /** 每页条数，缺省 20 */
    private Long pageSize = 20L;

    /** 操作人账号（可选，模糊匹配） */
    private String operatorName;

    /** 业务模块（可选，精确匹配） */
    private String module;

    /** 业务类型（可选，对应 {@code com.solo.common.enums.BusinessType} 名称） */
    private String businessType;

    /** 响应状态（可选）：{@code 0=失败}、{@code 1=成功} */
    private Integer responseStatus;

    /** 客户来源（可选，{@code web} / {@code app} 等） */
    private String clientSource;

    /** 起始时间（{@code yyyy-MM-dd HH:mm:ss}，GMT+8） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    /** 截止时间（{@code yyyy-MM-dd HH:mm:ss}，GMT+8） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;
}
