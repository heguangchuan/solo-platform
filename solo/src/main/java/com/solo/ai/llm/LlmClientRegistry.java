package com.solo.ai.llm;

import com.solo.ai.llm.client.AnthropicClient;
import com.solo.ai.llm.client.GeminiClient;
import com.solo.ai.llm.client.OpenAiCompatClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 在后端代码中维护"供应商名称 → 协议客户端"映射。
 *
 * <p>模型来源定义集中在 {@link AiModelProvider}，本类只负责把来源映射到已实现的协议客户端。</p>
 *
 * <p>未匹配到的 {@code provider} 会回退到 OpenAI 兼容客户端。</p>
 */
@Component
@RequiredArgsConstructor
public class LlmClientRegistry {

    private final OpenAiCompatClient openAiCompatClient;
    private final AnthropicClient anthropicClient;
    private final GeminiClient geminiClient;

    private final Map<AiModelProvider, LlmClient> protocolClients = new EnumMap<>(AiModelProvider.class);

    @PostConstruct
    void initRegistry() {
        for (AiModelProvider provider : AiModelProvider.values()) {
            protocolClients.put(provider, switch (provider.getProtocol()) {
                case "ANTHROPIC" -> anthropicClient;
                case "GEMINI" -> geminiClient;
                default -> openAiCompatClient;
            });
        }
    }

    /**
     * 根据 provider 解析出对应的客户端，未识别时返回 OpenAI 兼容客户端兜底。
     */
    public LlmClient resolve(String provider) {
        return AiModelProvider.resolve(provider)
                .map(protocolClients::get)
                .orElse(openAiCompatClient);
    }
}
