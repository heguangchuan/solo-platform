package com.solo.service.impl;

import com.solo.common.ServiceException;
import com.solo.entity.AiConversation;
import com.solo.entity.AiModelConfig;
import com.solo.request.ImageGenerationRequest;
import com.solo.request.VideoGenerationRequest;
import com.solo.request.ChatSendRequest;
import com.solo.response.ImageGenerationResponse;
import com.solo.response.VideoGenerationResponse;
import com.solo.service.AiChatService;
import com.solo.service.AiConversationService;
import com.solo.service.AiCreationService;
import com.solo.service.AiImageGenerationService;
import com.solo.service.AiIntentService;
import com.solo.service.AiMessageService;
import com.solo.service.AiModelConfigService;
import com.solo.service.AiVideoGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 统一创作编排实现。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiCreationServiceImpl implements AiCreationService {

    private final AiIntentService intentService;
    private final AiChatService chatService;
    private final AiImageGenerationService imageGenerationService;
    private final AiVideoGenerationService videoGenerationService;
    private final AiConversationService conversationService;
    private final AiMessageService messageService;
    private final AiModelConfigService modelConfigService;

    @Override
    public CreationResult send(Long conversationId, String content, List<String> images, Long userId,
                               Long modelConfigId, Consumer<String> sseConsumer) {
        if (content == null || content.isBlank()) {
            throw new ServiceException("请输入内容");
        }

        // 1. 解析/创建 MIXED 会话
        AiConversation conversation = resolveConversation(conversationId, content, userId);
        Long convId = conversation.getId();

        // 2. 文本模型（意图识别 + 对话共用）
        AiModelConfig textModel = resolveTextModel(modelConfigId, conversation.getModelConfigId());

        // 3. 意图识别
        AiIntentService.IntentResult intent = intentService.classify(convId, content, textModel);

        // 4. 用户提供的图片（上传/素材库）
        List<String> userImages = normalizeImages(images);

        // 5. 分发
        return switch (intent.intent()) {
            case "IMAGE" -> handleImage(convId, userId, intent, userImages, sseConsumer);
            case "VIDEO" -> handleVideo(convId, userId, intent, userImages, sseConsumer);
            default -> handleChat(convId, content, userImages, userId, textModel, sseConsumer);
        };
    }

    private CreationResult handleChat(Long convId, String content, List<String> userImages, Long userId,
                                      AiModelConfig textModel, Consumer<String> sseConsumer) {
        Long textModelId = textModel != null ? textModel.getId() : null;
        List<ChatSendRequest.AttachmentInput> attachments = toAttachmentInputs(userImages);
        AiChatService.StreamResult result = chatService.streamChat(convId, content, attachments, userId, textModelId, sseConsumer);
        return new CreationResult(result.conversationId(), "CHAT", null, result.reply());
    }

    private CreationResult handleImage(Long convId, Long userId, AiIntentService.IntentResult intent,
                                       List<String> userImages, Consumer<String> sseConsumer) {
        String placeholder = "正在生成图片…";
        sseConsumer.accept(placeholder);

        ImageGenerationRequest request = new ImageGenerationRequest();
        request.setConversationId(convId);
        request.setPrompt(intent.prompt());
        request.setImage(referenceImages(convId, intent.useLastImage(), userImages));

        ImageGenerationResponse response = imageGenerationService.generate(userId, request);
        return new CreationResult(convId, "IMAGE", response.getTaskId(), placeholder);
    }

    private CreationResult handleVideo(Long convId, Long userId, AiIntentService.IntentResult intent,
                                       List<String> userImages, Consumer<String> sseConsumer) {
        String placeholder = "正在生成视频，请稍候…";
        sseConsumer.accept(placeholder);

        VideoGenerationRequest request = new VideoGenerationRequest();
        request.setConversationId(convId);
        request.setPrompt(intent.prompt());
        request.setImage(referenceImages(convId, intent.useLastImage(), userImages));

        VideoGenerationResponse response = videoGenerationService.generate(userId, request);
        return new CreationResult(convId, "VIDEO", response.getTaskId(), placeholder);
    }

    /**
     * 参考图优先级：用户本轮显式提供的图片 > 接续会话最近一张生成图。
     */
    private List<String> referenceImages(Long convId, boolean useLastImage, List<String> userImages) {
        if (userImages != null && !userImages.isEmpty()) {
            return userImages;
        }
        if (!useLastImage) return null;
        String url = messageService.findLastGeneratedImageUrl(convId);
        return (url == null || url.isBlank()) ? null : List.of(url);
    }

    private List<String> normalizeImages(List<String> images) {
        if (images == null || images.isEmpty()) return null;
        List<String> cleaned = images.stream()
                .filter(url -> url != null && !url.isBlank())
                .toList();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private List<ChatSendRequest.AttachmentInput> toAttachmentInputs(List<String> images) {
        if (images == null || images.isEmpty()) return null;
        List<ChatSendRequest.AttachmentInput> inputs = new ArrayList<>();
        for (String url : images) {
            ChatSendRequest.AttachmentInput input = new ChatSendRequest.AttachmentInput();
            input.setUrl(url);
            input.setFileName(url.substring(url.lastIndexOf('/') + 1));
            inputs.add(input);
        }
        return inputs;
    }

    private AiConversation resolveConversation(Long conversationId, String content, Long userId) {
        if (conversationId == null) {
            return conversationService.createConversation(userId, "MIXED", generateTitle(content));
        }
        AiConversation conversation = conversationService.getById(conversationId);
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            throw new ServiceException("会话不存在");
        }
        return conversation;
    }

    /** 意图识别与对话都需要一个可用文本模型 */
    private AiModelConfig resolveTextModel(Long requestedId, Long conversationModelConfigId) {
        AiModelConfig model = pickTextModel(requestedId);
        if (model == null) {
            model = pickTextModel(conversationModelConfigId);
        }
        return model != null ? model : modelConfigService.getDefaultTextModel();
    }

    private AiModelConfig pickTextModel(Long id) {
        if (id == null) return null;
        AiModelConfig model = modelConfigService.getById(id);
        if (model == null || model.getStatus() == null || model.getStatus() != 1 || !"TEXT".equals(model.getModelType())) {
            return null;
        }
        return model;
    }

    private String generateTitle(String content) {
        if (content == null || content.isBlank()) return "智能创作";
        return content.length() <= 20 ? content : content.substring(0, 20) + "...";
    }
}
