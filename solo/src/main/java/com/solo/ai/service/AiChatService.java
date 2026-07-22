package com.solo.ai.service;

import com.solo.ai.entity.AiMessage;
import com.solo.ai.request.ChatSendRequest;

import java.util.List;

/**
 * AI对话服务接口
 * 负责处理与AI模型的交互
 */
public interface AiChatService {

    /**
     * 发送消息并获取回复（同步）
     */
    String chat(Long conversationId, String userMessage, Long userId, Long modelConfigId);

    /**
     * 流式发送消息，返回最终拼接的回复及会话ID
     */
    StreamResult streamChat(Long conversationId, String userMessage, Long userId, Long modelConfigId, java.util.function.Consumer<String> consumer);

    /**
     * 流式发送消息（多模态），返回最终拼接的回复及会话ID
     */
    StreamResult streamChat(Long conversationId, String userMessage, List<ChatSendRequest.AttachmentInput> images, Long userId, Long modelConfigId, java.util.function.Consumer<String> consumer);

    /**
     * 使用模板进行对话
     */
    String chatWithTemplate(Long conversationId, String templateCode, java.util.Map<String, Object> variables, Long userId, Long modelConfigId);

    /**
     * 构建对话消息列表（转换为API格式）
     */
    List<ChatMessage> buildChatMessages(List<AiMessage> messages);

    /**
     * 内部消息类
     */
    record ChatMessage(String role, String content, java.util.List<Attachment> attachments) {
        public static ChatMessage text(String role, String content) {
            return new ChatMessage(role, content, java.util.List.of());
        }
        public boolean hasAttachments() {
            return attachments != null && !attachments.isEmpty();
        }
    }

    /**
     * 多模态附件，目前仅支持 IMAGE 类型，url 必须可被供应商访问
     */
    record Attachment(String type, String url, String mimeType) {}

    /**
     * 流式对话结果
     */
    record StreamResult(Long conversationId, String reply) {}
}
