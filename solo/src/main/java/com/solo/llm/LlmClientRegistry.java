package com.solo.llm;

import com.solo.llm.client.AnthropicClient;
import com.solo.llm.client.GeminiClient;
import com.solo.llm.client.OpenAiCompatClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 在后端代码中维护"供应商名称 → 协议客户端"映射。
 *
 * <p>新增供应商时只需在 {@link #initRegistry()} 增加一行别名指向已有的协议客户端即可，
 * 不需要改 {@code ai_model_config} 表结构，也不需要前端配合。</p>
 *
 * <p>未匹配到的 {@code provider} 会回退到 OpenAI 兼容客户端。</p>
 */
@Component
@RequiredArgsConstructor
public class LlmClientRegistry {

    private final OpenAiCompatClient openAiCompatClient;
    private final AnthropicClient anthropicClient;
    private final GeminiClient geminiClient;

    private final Map<String, LlmClient> providerToClient = new HashMap<>();

    @PostConstruct
    void initRegistry() {
        // OpenAI Chat Completions 兼容协议（默认兜底）
        registerAliases(openAiCompatClient,
                "openai", "azure-openai", "azure",
                "agnes",
                "deepseek",
                "kimi", "moonshot",
                "qwen", "dashscope", "tongyi", "alibaba",
                "zhipu", "glm", "bigmodel",
                "ollama",
                "groq",
                "mistral",
                "xinference",
                "siliconflow",
                "perplexity",
                "together",
                "openrouter",
                "custom"
        );

        // Anthropic Messages API
        registerAliases(anthropicClient,
                "anthropic", "claude"
        );

        // Google Gemini Generate Content
        registerAliases(geminiClient,
                "google", "gemini", "vertex", "google-ai"
        );
    }

    private void registerAliases(LlmClient client, String... aliases) {
        for (String alias : aliases) {
            providerToClient.put(alias, client);
        }
    }

    /**
     * 根据 provider 解析出对应的客户端，未识别时返回 OpenAI 兼容客户端兜底。
     */
    public LlmClient resolve(String provider) {
        if (provider == null || provider.isBlank()) {
            return openAiCompatClient;
        }
        LlmClient client = providerToClient.get(provider.trim().toLowerCase());
        return client != null ? client : openAiCompatClient;
    }
}
