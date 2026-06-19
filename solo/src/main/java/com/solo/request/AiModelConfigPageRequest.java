package com.solo.request;

import lombok.Data;

/**
 * AI模型配置分页查询请求参数
 */
@Data
public class AiModelConfigPageRequest {
    private Long page = 1L;
    private Long pageSize = 20L;
    private String provider;
    private String modelType;
    private String modelName;
    private Integer status;
}
