package com.solo.ai.response;

import com.solo.ai.llm.AiModelProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 模型厂商选项
 *
 * <p>对应 {@code GET /api/ai/model-config/provider-options}，给前端"新增模型配置"页的"厂商"下拉用。
 * 系统内置的全部厂商：Agnes 视频、OpenAI、DeepSeek、Kimi / 月之暗面、通义千问 / 阿里百炼、
 * 智谱 GLM、Ollama（本地）、Groq、Mistral、Xinference、硅基流动、Perplexity、Together AI、
 * OpenRouter、Anthropic / Claude、Google Gemini、自定义 OpenAI 兼容接入点。</p>
 */
@Schema(description = "AI 模型厂商选项")
@Data
public class AiModelProviderOptionResponse {

    /** 厂商编码（提交到 {@code provider} 字段用，如 {@code openai} / {@code kimi} / {@code custom}） */
    @Schema(description = "厂商编码")
    private String value;
    /** 厂商展示名（前端下拉里给用户看，如 {@code "OpenAI"} / {@code "通义千问"}） */
    @Schema(description = "厂商展示名")
    private String label;
    /** 协议类型：{@code OPENAI_COMPAT} OpenAI 兼容 / {@code ANTHROPIC} Anthropic / {@code GEMINI} Gemini。前端可忽略 */
    @Schema(description = "协议类型：OPENAI_COMPAT OpenAI 兼容 / ANTHROPIC Anthropic / GEMINI Gemini")
    private String protocol;
    /** 该厂商的默认 API 地址（用户可填 {@code baseUrl} 覆盖；前端可作为 placeholder） */
    @Schema(description = "该厂商的默认 API 地址")
    private String defaultBaseUrl;
    /** 该厂商的旗舰模型是否支持图片输入（前端可作为"是否需要传图"的判断依据） */
    @Schema(description = "该厂商的旗舰模型是否支持图片输入")
    private Boolean supportVision;

    /**
     * 把 {@link com.solo.ai.llm.AiModelProvider} 转成下拉选项。
     */
    public static AiModelProviderOptionResponse from(com.solo.ai.llm.AiModelProvider provider) {
        AiModelProviderOptionResponse response = new AiModelProviderOptionResponse();
        response.setValue(provider.getCode());
        response.setLabel(provider.getLabel());
        response.setProtocol(provider.getProtocol());
        response.setDefaultBaseUrl(provider.getDefaultBaseUrl());
        response.setSupportVision(provider.isSupportVision());
        return response;
    }
}
