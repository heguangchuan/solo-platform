package com.solo.ai.llm;

import com.solo.ai.entity.AiModelConfig;
import com.solo.ai.service.AiChatService;

import java.util.List;
import java.util.function.Consumer;

/**
 * 针对单一供应商协议的 LLM 调用客户端。
 * <p>每个实现负责把通用的 ChatMessage 列表与 {@link AiModelConfig} 转换成对应协议的请求/响应格式。</p>
 */
public interface LlmClient {

    /**
     * 同步调用，返回完整回复内容。
     */
    String chat(AiModelConfig cfg, List<AiChatService.ChatMessage> messages);

    /**
     * 流式调用，每收到一段增量内容时通过 onChunk 回调，返回最终拼接的完整回复。
     */
    String streamChat(AiModelConfig cfg, List<AiChatService.ChatMessage> messages, Consumer<String> onChunk);

    /**
     * 当前客户端是否原生支持流式输出。
     * <p>返回 false 时上层会改为调用 {@link #chat} 并把完整结果以单段（或上层模拟分段）方式下发给消费者。</p>
     */
    default boolean supportsStreaming() {
        return true;
    }
}
