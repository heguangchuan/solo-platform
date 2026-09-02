package com.solo.system.request;

import lombok.Data;

/**
 * 参数分页查询请求参数
 *
 * <p>分页参数：{@code page} 从 1 开始，{@code pageSize} 默认 20。
 * 对应接口 {@code POST /api/config/page}。</p>
 */
@Data
public class ConfigPageRequest {

    /** 当前页码（从 1 开始），缺省 1 */
    private Long page = 1L;

    /** 每页条数，缺省 20 */
    private Long pageSize = 20L;

    /** 参数名称（可选，精确匹配） */
    private String configName;

    /** 参数编码（可选，精确匹配） */
    private String configKey;
}
