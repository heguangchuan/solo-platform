package com.solo.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.solo.entity.AiModelConfig;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * AI模型配置安全返回对象，不返回真实 API 密钥。
 */
@Data
public class AiModelConfigResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String provider;
    private String modelName;
    private String modelType;
    private String baseUrl;
    private String modelParams;
    private Integer supportVision;
    private Integer status;
    private Integer sort;
    private Boolean hasApiKey;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    public static AiModelConfigResponse from(AiModelConfig config) {
        if (config == null) return null;
        AiModelConfigResponse response = new AiModelConfigResponse();
        response.setId(config.getId());
        response.setProvider(config.getProvider());
        response.setModelName(config.getModelName());
        response.setModelType(config.getModelType());
        response.setBaseUrl(config.getBaseUrl());
        response.setModelParams(config.getModelParams());
        response.setSupportVision(config.getSupportVision());
        response.setStatus(config.getStatus());
        response.setSort(config.getSort());
        response.setCreateTime(config.getCreateTime());
        response.setUpdateTime(config.getUpdateTime());
        response.setHasApiKey(config.getApiKey() != null && !config.getApiKey().isBlank());
        return response;
    }
}
