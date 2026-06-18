package com.solo.service;

import com.solo.entity.AiMessage;

import java.util.List;

/**
 * AI对话服务接口
 * 负责处理与AI模型的交互
 */
public interface AiChatService {

    /**
     * 发送消息并获取回复（同步）
     */
    String chat(Long conversationId, String userMessage, Long userId);

    /**
     * 流式发送消息
     */
    void streamChat(Long conversationId, String userMessage, Long userId, java.util.function.Consumer<String> consumer);

    /**
     * 使用模板进行对话
     */
    String chatWithTemplate(Long conversationId, String templateCode, java.util.Map<String, Object> variables, Long userId);

    /**
     * 构建对话消息列表（转换为API格式）
     */
    List<ChatMessage> buildChatMessages(List<AiMessage> messages);

    /**
     * 内部消息类
     */
    record ChatMessage(String role, String content) {}
}
