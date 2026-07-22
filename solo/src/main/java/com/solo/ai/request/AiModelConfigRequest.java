package com.solo.ai.request;

import lombok.Data;

/**
 * AI模型配置新增/编辑请求体
 */
@Data
public class AiModelConfigRequest {
    private String provider;
    private String modelName;
    private String modelType;
    private String apiKey;
    private String baseUrl;
    private String modelParams;
    private Integer supportVision;
    private Integer status;
    private Integer sort;
}
