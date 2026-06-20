package com.solo.response;

import com.solo.llm.AiModelProvider;
import lombok.Data;

/**
 * AI模型来源选项返回对象。
 */
@Data
public class AiModelProviderOptionResponse {

    private String value;
    private String label;
    private String protocol;
    private String defaultBaseUrl;
    private Boolean supportVision;

    public static AiModelProviderOptionResponse from(AiModelProvider provider) {
        AiModelProviderOptionResponse response = new AiModelProviderOptionResponse();
        response.setValue(provider.getCode());
        response.setLabel(provider.getLabel());
        response.setProtocol(provider.getProtocol());
        response.setDefaultBaseUrl(provider.getDefaultBaseUrl());
        response.setSupportVision(provider.isSupportVision());
        return response;
    }
}
