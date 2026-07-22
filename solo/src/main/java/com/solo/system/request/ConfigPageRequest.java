package com.solo.system.request;

import lombok.Data;

/**
 * 参数分页查询请求参数
 */
@Data
public class ConfigPageRequest {
    private Long page = 1L;
    private Long pageSize = 20L;
    private String configName;
    private String configKey;
}
