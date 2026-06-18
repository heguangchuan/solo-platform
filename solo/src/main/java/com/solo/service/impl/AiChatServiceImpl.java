package com.solo.service.impl;

import com.solo.entity.AiConversation;
import com.solo.entity.AiMessage;
import com.solo.entity.AiModelConfig;
import com.solo.service.*;
import com.solo.common.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI对话服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatServiceImpl implements AiChatService {

    private final AiConversationService conversationService;
    private final AiMessageService messageService;
    private final AiPromptTemplateService promptTemplateService;
    private final AiModelConfigService modelConfigService;
    private final AiGenerationTaskService generationTaskService;

    @Override
    @Transactional
    public String chat(Long conversationId, String userMessage, Long userId) {
        // 1. 如果没有会话ID，创建新会话
        AiConversation conversation;
        if (conversationId == null) {
            String title = generateTitle(userMessage);
            conversation = conversationService.createConversation(userId, "CHAT", title);
            conversationId = conversation.getId();
        } else {
            conversation = conversationService.getById(conversationId);
            if (conversation == null) {
                throw new ServiceException("会话不存在");
            }
        }

        // 2. 保存用户消息
        AiMessage userMsg = messageService.addUserMessage(conversationId, userMessage);

        // 3. 获取模型配置
        AiModelConfig modelConfig = modelConfigService.getDefaultTextModel();
        if (modelConfig == null) {
            throw new ServiceException("未配置可用的AI模型");
        }

        // 4. 构建上下文消息
        List<AiMessage> historyMessages = messageService.getRecentMessages(conversationId, 10);
        List<ChatMessage> chatMessages = buildChatMessages(historyMessages);

        // 5. 调用模型生成回复（这里模拟生成，实际需要接入真实API）
        String assistantReply = callModel(chatMessages, modelConfig);

        // 6. 保存助手消息
        AiMessage assistantMsg = messageService.addAssistantMessage(conversationId, assistantReply, modelConfig.getModelName(), 0);

        // 7. 创建并完成生成任务
        generationTaskService.createTextTask(userId, conversationId, assistantMsg.getId(), userMessage, userMessage, modelConfig.getId(), modelConfig.getModelName());
        generationTaskService.markSuccess(assistantMsg.getId(), assistantReply, 0, BigDecimal.ZERO);

        // 8. 如果是第一条消息，更新会话标题
        if (conversation.getMessageCount() <= 2) {
            String newTitle = generateTitle(userMessage);
            conversationService.updateTitle(conversationId, newTitle);
        }

        return assistantReply;
    }

    @Override
    public void streamChat(Long conversationId, String userMessage, Long userId, Consumer<String> consumer) {
        // 流式实现
        String reply = chat(conversationId, userMessage, userId);
        // 模拟流式输出
        for (char c : reply.toCharArray()) {
            consumer.accept(String.valueOf(c));
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    @Transactional
    public String chatWithTemplate(Long conversationId, String templateCode, Map<String, Object> variables, Long userId) {
        String renderedPrompt = promptTemplateService.renderTemplate(templateCode, variables);
        return chat(conversationId, renderedPrompt, userId);
    }

    @Override
    public List<ChatMessage> buildChatMessages(List<AiMessage> messages) {
        List<ChatMessage> result = new ArrayList<>();
        for (AiMessage msg : messages) {
            String role = switch (msg.getRole()) {
                case "USER" -> "user";
                case "ASSISTANT" -> "assistant";
                case "SYSTEM" -> "system";
                default -> "user";
            };
            result.add(new ChatMessage(role, msg.getContent()));
        }
        return result;
    }

    /**
     * 模拟调用AI模型生成回复
     * 实际项目中需要接入真实的AI API（如OpenAI、DeepSeek等）
     */
    private String callModel(List<ChatMessage> messages, AiModelConfig modelConfig) {
        ChatMessage lastMessage = messages.getLast();
        String userMessage = lastMessage.content();

        // 简单的模拟回复
        return String.format("收到您的消息：「%s」\n\n这是 Agnes 的模拟回复。在实际应用中，我会调用配置好的 AI 模型（如 %s）来生成智能回复。\n\n您可以继续和我对话，或者选择使用创作模板来生成文案。",
                userMessage, modelConfig.getModelName());
    }

    /**
     * 根据用户消息生成会话标题（简化版）
     */
    private String generateTitle(String userMessage) {
        if (userMessage == null || userMessage.isEmpty()) {
            return "新对话";
        }
        int maxLength = 20;
        if (userMessage.length() <= maxLength) {
            return userMessage;
        }
        return userMessage.substring(0, maxLength) + "...";
    }
}
