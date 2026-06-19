package com.solo.service.impl;

import com.solo.common.ServiceException;
import com.solo.entity.AiConversation;
import com.solo.entity.AiGenerationTask;
import com.solo.entity.AiMessage;
import com.solo.entity.AiModelConfig;
import com.solo.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AI对话服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatServiceImpl implements AiChatService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final AiConversationService conversationService;
    private final AiMessageService messageService;
    private final AiPromptTemplateService promptTemplateService;
    private final AiModelConfigService modelConfigService;
    private final AiGenerationTaskService generationTaskService;

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
        String assistantReply = callModel(chatMessages, modelConfig);

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

        // 5. 流式调用模型
        StringBuilder buffer = new StringBuilder();
        String reply;
        try {
            reply = streamCallModel(chatMessages, modelConfig, chunk -> {
                buffer.append(chunk);
                consumer.accept(chunk);
            });
        } catch (RuntimeException e) {
            log.warn("AI模型流式调用失败，回退非流式：{}", e.getMessage());
            reply = callModel(chatMessages, modelConfig);
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

    private String streamCallModel(List<ChatMessage> messages, AiModelConfig modelConfig, Consumer<String> chunkConsumer) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelConfig.getModelName());
        requestBody.put("messages", messages.stream()
                .map(msg -> Map.of("role", msg.role(), "content", msg.content()))
                .toList());
        applyModelParams(requestBody, modelConfig.getModelParams());
        requestBody.put("stream", true);

        String body;
        try {
            body = MAPPER.writeValueAsString(requestBody);
        } catch (Exception e) {
            throw new ServiceException("AI模型请求构建失败", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(modelConfig.getBaseUrl()))
                .header("Authorization", "Bearer " + modelConfig.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        StringBuilder full = new StringBuilder();
        try {
            HttpResponse<java.io.InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errBody;
                try (var es = response.body()) {
                    errBody = new String(es.readAllBytes(), StandardCharsets.UTF_8);
                }
                log.warn("AI模型流式调用失败：status={}, body={}", response.statusCode(), errBody);
                throw new ServiceException("AI模型流式调用失败");
            }

            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.isEmpty()) continue;
                    if ("[DONE]".equals(data)) break;
                    try {
                        JsonNode chunk = MAPPER.readTree(data);
                        JsonNode delta = chunk.path("choices").path(0).path("delta").path("content");
                        if (!delta.isMissingNode() && !delta.isNull()) {
                            String piece = delta.asText();
                            if (!piece.isEmpty()) {
                                full.append(piece);
                                chunkConsumer.accept(piece);
                            }
                        }
                    } catch (Exception ex) {
                        log.debug("流式分片解析失败: {}", ex.getMessage());
                    }
                }
            }
            return full.toString();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI模型流式调用异常：{}", e.getMessage(), e);
            throw new ServiceException("AI模型流式调用异常", e);
        }
    }

    /**
     * 调用 Agnes/OpenAI 兼容 Chat Completions 接口生成回复
     */
    private String callModel(List<ChatMessage> messages, AiModelConfig modelConfig) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", modelConfig.getModelName());
            requestBody.put("messages", messages.stream()
                    .map(msg -> Map.of("role", msg.role(), "content", msg.content()))
                    .toList());
            applyModelParams(requestBody, modelConfig.getModelParams());

            String body = MAPPER.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(modelConfig.getBaseUrl()))
                    .header("Authorization", "Bearer " + modelConfig.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI模型调用失败：status={}, body={}", response.statusCode(), response.body());
                throw new ServiceException("AI模型调用失败");
            }

            JsonNode root = MAPPER.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                throw new ServiceException("AI模型响应格式异常");
            }
            return contentNode.asText();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI模型调用异常：{}", e.getMessage(), e);
            throw new ServiceException("AI模型调用异常", e);
        }
    }

    private void applyModelParams(Map<String, Object> requestBody, String modelParams) {
        if (modelParams == null || modelParams.isBlank()) {
            requestBody.put("temperature", 0.5);
            requestBody.put("max_tokens", 1024);
            return;
        }
        try {
            JsonNode params = MAPPER.readTree(modelParams);
            params.fields().forEachRemaining(entry -> requestBody.put(entry.getKey(), MAPPER.convertValue(entry.getValue(), Object.class)));
        } catch (Exception e) {
            log.warn("模型参数解析失败，使用默认参数：{}", e.getMessage());
            requestBody.put("temperature", 0.5);
            requestBody.put("max_tokens", 1024);
        }
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
