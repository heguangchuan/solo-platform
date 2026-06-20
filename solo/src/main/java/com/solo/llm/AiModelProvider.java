package com.solo.llm;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * AI模型来源定义。
 */
@Getter
public enum AiModelProvider {
    AGNES("agnes", "Agnes", "OPENAI_COMPAT", "https://apihub.agnes-ai.com/v1/chat/completions", true,
            "agnes"),
    OPENAI("openai", "OpenAI", "OPENAI_COMPAT", "https://api.openai.com/v1/chat/completions", true,
            "openai", "azure-openai", "azure"),
    DEEPSEEK("deepseek", "DeepSeek", "OPENAI_COMPAT", "https://api.deepseek.com/v1/chat/completions", false,
            "deepseek"),
    KIMI("kimi", "Kimi / Moonshot", "OPENAI_COMPAT", "https://api.moonshot.cn/v1/chat/completions", false,
            "kimi", "moonshot"),
    QWEN("qwen", "通义千问", "OPENAI_COMPAT", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", true,
            "qwen", "dashscope", "tongyi", "alibaba"),
    ZHIPU("zhipu", "智谱 GLM", "OPENAI_COMPAT", "https://open.bigmodel.cn/api/paas/v4/chat/completions", false,
            "zhipu", "glm", "bigmodel"),
    OLLAMA("ollama", "Ollama", "OPENAI_COMPAT", "http://localhost:11434/v1/chat/completions", true,
            "ollama"),
    GROQ("groq", "Groq", "OPENAI_COMPAT", "https://api.groq.com/openai/v1/chat/completions", false,
            "groq"),
    MISTRAL("mistral", "Mistral", "OPENAI_COMPAT", "https://api.mistral.ai/v1/chat/completions", false,
            "mistral"),
    XINFERENCE("xinference", "Xinference", "OPENAI_COMPAT", "", false,
            "xinference"),
    SILICONFLOW("siliconflow", "硅基流动", "OPENAI_COMPAT", "https://api.siliconflow.cn/v1/chat/completions", false,
            "siliconflow"),
    PERPLEXITY("perplexity", "Perplexity", "OPENAI_COMPAT", "https://api.perplexity.ai/chat/completions", false,
            "perplexity"),
    TOGETHER("together", "Together AI", "OPENAI_COMPAT", "https://api.together.xyz/v1/chat/completions", false,
            "together"),
    OPENROUTER("openrouter", "OpenRouter", "OPENAI_COMPAT", "https://openrouter.ai/api/v1/chat/completions", true,
            "openrouter"),
    ANTHROPIC("anthropic", "Anthropic / Claude", "ANTHROPIC", "https://api.anthropic.com/v1/messages", true,
            "anthropic", "claude"),
    GEMINI("gemini", "Google Gemini", "GEMINI", "https://generativelanguage.googleapis.com/v1beta/models", true,
            "google", "gemini", "vertex", "google-ai"),
    CUSTOM("custom", "自定义 OpenAI 兼容", "OPENAI_COMPAT", "", false,
            "custom");

    private final String code;
    private final String label;
    private final String protocol;
    private final String defaultBaseUrl;
    private final boolean supportVision;
    private final List<String> aliases;

    AiModelProvider(String code, String label, String protocol, String defaultBaseUrl, boolean supportVision, String... aliases) {
        this.code = code;
        this.label = label;
        this.protocol = protocol;
        this.defaultBaseUrl = defaultBaseUrl;
        this.supportVision = supportVision;
        this.aliases = List.of(aliases);
    }

    public static Optional<AiModelProvider> resolve(String provider) {
        if (provider == null || provider.isBlank()) {
            return Optional.empty();
        }
        String normalized = provider.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(item -> item.code.equals(normalized) || item.aliases.contains(normalized))
                .findFirst();
    }
}
