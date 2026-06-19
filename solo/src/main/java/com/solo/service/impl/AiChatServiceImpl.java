package com.solo.service.impl;

import com.solo.common.ServiceException;
import com.solo.entity.AiAsset;
import com.solo.entity.AiConversation;
import com.solo.entity.AiGenerationTask;
import com.solo.entity.AiMessage;
import com.solo.entity.AiModelConfig;
import com.solo.llm.HttpClientHolder;
import com.solo.llm.LlmClient;
import com.solo.llm.LlmClientRegistry;
import com.solo.request.ChatSendRequest;
import com.solo.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    /** 上下文中保留图片的最近用户消息条数，更早的消息只保留文本，避免 token 暴涨 */
    private static final int RECENT_VISION_TURNS = 3;

    private final AiConversationService conversationService;
    private final AiMessageService messageService;
    private final AiPromptTemplateService promptTemplateService;
    private final AiModelConfigService modelConfigService;
    private final AiGenerationTaskService generationTaskService;
    private final AiAssetService assetService;
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
        messageService.addUserMessage(conversationId, userMessage);

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

        // 5. 提前创建生成任务（PENDING），失败也保留记录
        AiGenerationTask task = generationTaskService.createTextTask(
                userId, conversationId, null,
                userMessage, userMessage,
                modelConfig.getId(), modelConfig.getModelName());

        // 6. 调用模型生成回复
        LlmClient client = llmClientRegistry.resolve(modelConfig.getProvider());
        String assistantReply;
        try {
            assistantReply = client.chat(modelConfig, chatMessages);
        } catch (RuntimeException e) {
            generationTaskService.markFailed(task.getId(), shortenError(e.getMessage()));
            throw e;
        }

        // 7. 保存助手消息
        AiMessage assistantMsg = messageService.addAssistantMessage(conversationId, assistantReply, modelConfig.getModelName(), 0);

        // 8. 关联任务到助手消息并标记成功
        task.setMessageId(assistantMsg.getId());
        generationTaskService.updateById(task);
        generationTaskService.markSuccess(task.getId(), assistantReply, 0, BigDecimal.ZERO);

        // 9. 如果是第一条消息，更新会话标题
        if (conversation.getMessageCount() <= 2) {
            String newTitle = generateTitle(userMessage);
            conversationService.updateTitle(conversationId, newTitle);
        }

        return assistantReply;
    }

    @Override
    public StreamResult streamChat(Long conversationId, String userMessage, Long userId, Long modelConfigId, Consumer<String> consumer) {
        return streamChat(conversationId, userMessage, null, userId, modelConfigId, consumer);
    }

    @Override
    public StreamResult streamChat(Long conversationId, String userMessage, List<ChatSendRequest.AttachmentInput> images,
                                   Long userId, Long modelConfigId, Consumer<String> consumer) {
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

        // 2. 处理附件：落库 ai_asset，构造给消息保存的 attachments JSON
        List<Map<String, Object>> attachmentEntries = persistAttachments(images, userId);
        String attachmentsJson = serializeAttachments(attachmentEntries);

        // 3. 保存用户消息（含附件）
        messageService.addUserMessage(currentConversationId, userMessage, attachmentsJson);

        // 4. 模型配置
        AiModelConfig modelConfig = resolveModelConfig(modelConfigId, conversation.getModelConfigId());
        if (modelConfig == null) {
            throw new ServiceException("未配置可用的AI模型");
        }
        if (conversation.getModelConfigId() == null || !conversation.getModelConfigId().equals(modelConfig.getId())) {
            conversation.setModelConfigId(modelConfig.getId());
            conversationService.updateById(conversation);
        }

        // 5. 上下文（最近 N 条用户消息保留图片）
        List<AiMessage> historyMessages = messageService.getRecentMessages(currentConversationId, 10);
        List<ChatMessage> chatMessages = buildChatMessages(historyMessages);

        // 6. 提前创建生成任务（PENDING），无论模型成功失败都有完整记录
        AiGenerationTask task = generationTaskService.createTextTask(
                userId, currentConversationId, null,
                userMessage, userMessage,
                modelConfig.getId(), modelConfig.getModelName());

        // 7. 决定是否走流式
        LlmClient client = llmClientRegistry.resolve(modelConfig.getProvider());
        boolean useStream = client.supportsStreaming() && isStreamEnabled(modelConfig);

        StringBuilder buffer = new StringBuilder();
        String reply;
        try {
            if (useStream) {
                try {
                    reply = client.streamChat(modelConfig, chatMessages, chunk -> {
                        buffer.append(chunk);
                        consumer.accept(chunk);
                    });
                } catch (RuntimeException e) {
                    log.warn("AI模型流式调用失败，回退非流式：{}", e.getMessage());
                    reply = client.chat(modelConfig, chatMessages);
                    emitRemainder(buffer.toString(), reply, consumer);
                }
            } else {
                log.debug("模型 {} 不支持流式，直接调用非流式接口", modelConfig.getModelName());
                reply = client.chat(modelConfig, chatMessages);
                emitRemainder("", reply, consumer);
            }
        } catch (RuntimeException e) {
            // 模型调用最终失败：记录失败任务，再把异常抛给上层（SSE 控制器）
            generationTaskService.markFailed(task.getId(), shortenError(e.getMessage()));
            throw e;
        }

        // 8. 保存助手消息
        AiMessage assistantMsg = messageService.addAssistantMessage(currentConversationId, reply, modelConfig.getModelName(), 0);

        // 9. 把任务关联到助手消息并标记成功
        task.setMessageId(assistantMsg.getId());
        generationTaskService.updateById(task);
        generationTaskService.markSuccess(task.getId(), reply, 0, BigDecimal.ZERO);

        // 10. 第一条消息时刷新会话标题
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
        // 上下文中只对最近 RECENT_VISION_TURNS 条用户消息保留图片
        int total = messages.size();
        boolean[] keepImage = new boolean[total];
        int userMsgFromEnd = 0;
        for (int i = total - 1; i >= 0; i--) {
            AiMessage msg = messages.get(i);
            if ("USER".equals(msg.getRole())) {
                userMsgFromEnd++;
                keepImage[i] = userMsgFromEnd <= RECENT_VISION_TURNS;
            }
        }

        List<ChatMessage> result = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            AiMessage msg = messages.get(i);
            String role = switch (msg.getRole()) {
                case "ASSISTANT" -> "assistant";
                case "SYSTEM" -> "system";
                default -> "user";
            };
            List<Attachment> attachments = "user".equals(role) && keepImage[i]
                    ? parseAttachments(msg.getAttachments())
                    : List.of();
            result.add(new ChatMessage(role, msg.getContent(), attachments));
        }
        return result;
    }

    private List<Attachment> parseAttachments(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode arr = HttpClientHolder.MAPPER.readTree(json);
            if (!arr.isArray()) return List.of();
            List<Attachment> list = new ArrayList<>();
            for (JsonNode node : arr) {
                String url = node.path("url").asText("");
                if (url.isBlank()) continue;
                String type = node.path("type").asText("IMAGE");
                String mime = node.path("mimeType").asText(null);
                list.add(new Attachment(type, url, mime));
            }
            return list;
        } catch (Exception e) {
            log.debug("解析消息附件失败：{}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 把上传上来的图片落库到 ai_asset，返回用于消息持久化的 JSON 节点列表。
     */
    private List<Map<String, Object>> persistAttachments(List<ChatSendRequest.AttachmentInput> images, Long userId) {
        if (images == null || images.isEmpty()) return List.of();
        List<Map<String, Object>> entries = new ArrayList<>();
        for (ChatSendRequest.AttachmentInput input : images) {
            if (input == null || input.getUrl() == null || input.getUrl().isBlank()) continue;
            AiAsset asset = new AiAsset();
            asset.setUserId(userId);
            asset.setAssetType("IMAGE");
            asset.setFileName(input.getFileName() != null ? input.getFileName() : "image");
            asset.setFileUrl(input.getUrl());
            asset.setMimeType(input.getMimeType());
            asset.setStatus(1);
            try {
                assetService.saveAsset(asset);
            } catch (Exception e) {
                log.warn("素材落库失败，但不影响对话：{}", e.getMessage());
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "IMAGE");
            entry.put("url", input.getUrl());
            if (input.getFileName() != null) entry.put("fileName", input.getFileName());
            if (input.getMimeType() != null) entry.put("mimeType", input.getMimeType());
            if (asset.getId() != null) entry.put("assetId", asset.getId().toString());
            entries.add(entry);
        }
        return entries;
    }

    private String serializeAttachments(List<Map<String, Object>> entries) {
        if (entries == null || entries.isEmpty()) return null;
        try {
            return HttpClientHolder.MAPPER.writeValueAsString(entries);
        } catch (Exception e) {
            log.warn("附件序列化失败：{}", e.getMessage());
            return null;
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
     * 解析 model_params 中的 stream 字段，允许在配置层面强制关闭流式（默认开启）。
     */
    private boolean isStreamEnabled(AiModelConfig modelConfig) {
        String params = modelConfig.getModelParams();
        if (params == null || params.isBlank()) return true;
        try {
            JsonNode node = HttpClientHolder.MAPPER.readTree(params).path("stream");
            if (node.isMissingNode() || node.isNull()) return true;
            if (node.isBoolean()) return node.asBoolean(true);
            return !"false".equalsIgnoreCase(node.asText());
        } catch (Exception e) {
            log.debug("解析 model_params.stream 失败，默认走流式：{}", e.getMessage());
            return true;
        }
    }

    /**
     * 当流式没有产生（或不完整）时，把剩余内容补给消费者，保证前端显示完整。
     */
    private void emitRemainder(String already, String fullReply, Consumer<String> consumer) {
        if (fullReply == null || fullReply.isEmpty()) return;
        if (already == null || already.isEmpty()) {
            consumer.accept(fullReply);
            return;
        }
        if (fullReply.length() > already.length() && fullReply.startsWith(already)) {
            consumer.accept(fullReply.substring(already.length()));
        } else if (!fullReply.equals(already)) {
            consumer.accept(fullReply);
        }
    }

    /**
     * 截断错误信息，避免超过 ai_generation_task.error_msg 长度上限
     */
    private String shortenError(String message) {
        if (message == null || message.isBlank()) return "未知错误";
        return message.length() > 1000 ? message.substring(0, 1000) : message;
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
