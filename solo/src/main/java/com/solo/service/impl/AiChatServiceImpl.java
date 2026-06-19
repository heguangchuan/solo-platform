package com.solo.service.impl;

import com.solo.common.ServiceException;
import com.solo.entity.AiConversation;
import com.solo.entity.AiGenerationTask;
import com.solo.entity.AiMessage;
import com.solo.entity.AiModelConfig;
import com.solo.llm.LlmClient;
import com.solo.llm.LlmClientRegistry;
import com.solo.service.*;
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
    private final LlmClientRegistry llmClientRegistry;

    @Override
    @Transactional
    public String chat(Long conversationId, String userMessage, Long userId, Long modelConfigId) {
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
        AiModelConfig modelConfig = resolveModelConfig(modelConfigId, conversation.getModelConfigId());
        if (modelConfig == null) {
            throw new ServiceException("未配置可用的AI模型");
        }
        if (conversation.getModelConfigId() == null || !conversation.getModelConfigId().equals(modelConfig.getId())) {
            conversation.setModelConfigId(modelConfig.getId());
            conversationService.updateById(conversation);
        }

        // 4. 构建上下文消息
        List<AiMessage> historyMessages = messageService.getRecentMessages(conversationId, 10);
        List<ChatMessage> chatMessages = buildChatMessages(historyMessages);

        // 5. 调用模型生成回复
        LlmClient client = llmClientRegistry.resolve(modelConfig.getProvider());
        String assistantReply = client.chat(modelConfig, chatMessages);

        // 6. 保存助手消息
        AiMessage assistantMsg = messageService.addAssistantMessage(conversationId, assistantReply, modelConfig.getModelName(), 0);

        // 7. 创建并完成生成任务
        AiGenerationTask task = generationTaskService.createTextTask(userId, conversationId, assistantMsg.getId(), userMessage, userMessage, modelConfig.getId(), modelConfig.getModelName());
        generationTaskService.markSuccess(task.getId(), assistantReply, 0, BigDecimal.ZERO);

        // 8. 如果是第一条消息，更新会话标题
        if (conversation.getMessageCount() <= 2) {
            String newTitle = generateTitle(userMessage);
            conversationService.updateTitle(conversationId, newTitle);
        }

        return assistantReply;
    }

    @Override
    public StreamResult streamChat(Long conversationId, String userMessage, Long userId, Long modelConfigId, Consumer<String> consumer) {
        // 1. 会话准备：创建或加载
        AiConversation conversation;
        if (conversationId == null) {
            conversation = conversationService.createConversation(userId, "CHAT", generateTitle(userMessage));
        } else {
            conversation = conversationService.getById(conversationId);
            if (conversation == null) {
                throw new ServiceException("会话不存在");
            }
            if (!userId.equals(conversation.getUserId())) {
                throw new ServiceException("无权访问此会话");
            }
        }
        Long currentConversationId = conversation.getId();

        // 2. 保存用户消息
        messageService.addUserMessage(currentConversationId, userMessage);

        // 3. 模型配置
        AiModelConfig modelConfig = resolveModelConfig(modelConfigId, conversation.getModelConfigId());
        if (modelConfig == null) {
            throw new ServiceException("未配置可用的AI模型");
        }
        if (conversation.getModelConfigId() == null || !conversation.getModelConfigId().equals(modelConfig.getId())) {
            conversation.setModelConfigId(modelConfig.getId());
            conversationService.updateById(conversation);
        }

        // 4. 上下文
        List<AiMessage> historyMessages = messageService.getRecentMessages(currentConversationId, 10);
        List<ChatMessage> chatMessages = buildChatMessages(historyMessages);

        // 5. 流式调用模型，失败时回退非流式
        LlmClient client = llmClientRegistry.resolve(modelConfig.getProvider());
        StringBuilder buffer = new StringBuilder();
        String reply;
        try {
            reply = client.streamChat(modelConfig, chatMessages, chunk -> {
                buffer.append(chunk);
                consumer.accept(chunk);
            });
        } catch (RuntimeException e) {
            log.warn("AI模型流式调用失败，回退非流式：{}", e.getMessage());
            reply = client.chat(modelConfig, chatMessages);
            String already = buffer.toString();
            if (reply.length() > already.length() && reply.startsWith(already)) {
                consumer.accept(reply.substring(already.length()));
            } else if (already.isEmpty()) {
                consumer.accept(reply);
            }
        }

        // 6. 保存助手消息
        AiMessage assistantMsg = messageService.addAssistantMessage(currentConversationId, reply, modelConfig.getModelName(), 0);

        // 7. 创建并完成生成任务
        AiGenerationTask task = generationTaskService.createTextTask(userId, currentConversationId, assistantMsg.getId(), userMessage, userMessage, modelConfig.getId(), modelConfig.getModelName());
        generationTaskService.markSuccess(task.getId(), reply, 0, BigDecimal.ZERO);

        // 8. 第一条消息时刷新会话标题
        if (conversation.getMessageCount() <= 2) {
            conversationService.updateTitle(currentConversationId, generateTitle(userMessage));
        }

        return new StreamResult(currentConversationId, reply);
    }

    @Override
    @Transactional
    public String chatWithTemplate(Long conversationId, String templateCode, Map<String, Object> variables, Long userId, Long modelConfigId) {
        String renderedPrompt = promptTemplateService.renderTemplate(templateCode, variables);
        return chat(conversationId, renderedPrompt, userId, modelConfigId);
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

    private AiModelConfig resolveModelConfig(Long requestedModelConfigId, Long conversationModelConfigId) {
        AiModelConfig modelConfig = null;
        if (requestedModelConfigId != null) {
            modelConfig = modelConfigService.getById(requestedModelConfigId);
            if (modelConfig == null || modelConfig.getStatus() == null || modelConfig.getStatus() != 1 || !"TEXT".equals(modelConfig.getModelType())) {
                throw new ServiceException("模型配置不可用");
            }
        } else if (conversationModelConfigId != null) {
            modelConfig = modelConfigService.getById(conversationModelConfigId);
            if (modelConfig == null || modelConfig.getStatus() == null || modelConfig.getStatus() != 1 || !"TEXT".equals(modelConfig.getModelType())) {
                modelConfig = null;
            }
        }
        return modelConfig != null ? modelConfig : modelConfigService.getDefaultTextModel();
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
