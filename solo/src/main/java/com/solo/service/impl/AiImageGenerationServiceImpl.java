package com.solo.service.impl;

import com.solo.common.ServiceException;
import com.solo.entity.AiAsset;
import com.solo.entity.AiConversation;
import com.solo.entity.AiGenerationTask;
import com.solo.entity.AiMessage;
import com.solo.entity.AiModelConfig;
import com.solo.entity.SysFile;
import com.solo.llm.HttpClientHolder;
import com.solo.llm.ImageGenerationClient;
import com.solo.llm.ImageGenerationClientRegistry;
import com.solo.llm.ImageGenerationResult;
import com.solo.request.ImageGenerationRequest;
import com.solo.response.ImageGenerationResponse;
import com.solo.service.AiAssetService;
import com.solo.service.AiConversationService;
import com.solo.service.AiGenerationTaskService;
import com.solo.service.AiImageGenerationService;
import com.solo.service.AiMessageService;
import com.solo.service.AiModelConfigService;
import com.solo.service.SysFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片生成服务实现。
 */
@Service
@RequiredArgsConstructor
public class AiImageGenerationServiceImpl implements AiImageGenerationService {

    private final AiModelConfigService modelConfigService;
    private final AiGenerationTaskService generationTaskService;
    private final AiConversationService conversationService;
    private final AiMessageService messageService;
    private final AiAssetService assetService;
    private final SysFileService fileService;
    private final com.solo.service.SysUserService sysUserService;
    private final ImageGenerationClientRegistry imageClientRegistry;
    @Qualifier("aiTaskExecutor")
    private final ThreadPoolTaskExecutor aiTaskExecutor;

    @Override
    public ImageGenerationResponse generate(Long userId, ImageGenerationRequest request) {
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new ServiceException("请输入图片描述");
        }
        AiModelConfig modelConfig = resolveModelConfig(request.getModelConfigId());
        AiConversation conversation = resolveConversation(userId, request.getConversationId(), request.getPrompt(), modelConfig.getId());
        String userAttachments = serializeReferenceImages(request.getImage());
        AiMessage userMessage = messageService.addUserMessage(conversation.getId(), request.getPrompt(), userAttachments);
        AiGenerationTask task = generationTaskService.createImageTask(userId, conversation.getId(), userMessage.getId(), request.getPrompt(), modelConfig.getId(), modelConfig.getModelName(), modelConfig.getModelParams());
        String username = sysUserService.getById(userId).getUsername();
        aiTaskExecutor.execute(() -> doGenerate(task.getId(), userId, username, conversation.getId(), request, modelConfig));

        ImageGenerationResponse response = new ImageGenerationResponse();
        response.setConversationId(conversation.getId());
        response.setTaskId(task.getId());
        response.setModelName(modelConfig.getModelName());
        response.setStatus(task.getStatus());
        response.setImages(List.of());
        return response;
    }

    private void doGenerate(Long taskId, Long userId, String username, Long conversationId, ImageGenerationRequest request, AiModelConfig modelConfig) {
        try {
            ImageGenerationClient client = imageClientRegistry.resolve(modelConfig.getProvider());
            ImageGenerationResult result = client.generate(modelConfig, request);
            List<ImageGenerationResponse.ImageItem> images = saveAssets(userId, taskId, request.getPrompt(), modelConfig.getModelName(), persistImages(taskId, userId, username, result.urls()));
            String resultContent = writeJson(images);
            messageService.addAssistantImageMessage(conversationId, "已生成 " + images.size() + " 张图片", resultContent, modelConfig.getModelName());
            generationTaskService.markSuccess(taskId, "URL", resultContent, null, null);
        } catch (Exception e) {
            generationTaskService.markFailed(taskId, shortenError(e.getMessage()));
        }
    }

    private AiConversation resolveConversation(Long userId, Long conversationId, String prompt, Long modelConfigId) {
        if (conversationId != null) {
            AiConversation conversation = conversationService.getById(conversationId);
            if (conversation == null || !userId.equals(conversation.getUserId()) || !"IMAGE".equals(conversation.getConversationType())) {
                throw new ServiceException("会话不存在");
            }
            if (!modelConfigId.equals(conversation.getModelConfigId())) {
                conversation.setModelConfigId(modelConfigId);
                conversationService.updateById(conversation);
            }
            return conversation;
        }
        AiConversation conversation = conversationService.createConversation(userId, "IMAGE", generateTitle(prompt));
        conversation.setModelConfigId(modelConfigId);
        conversationService.updateById(conversation);
        return conversation;
    }

    private String generateTitle(String prompt) {
        if (prompt == null || prompt.isBlank()) return "图片创作";
        return prompt.length() <= 20 ? prompt : prompt.substring(0, 20) + "...";
    }

    private String serializeReferenceImages(List<String> urls) {
        if (urls == null || urls.isEmpty()) return null;
        List<ImageGenerationResponse.ImageItem> images = new ArrayList<>();
        for (String url : urls) {
            if (url == null || url.isBlank()) continue;
            ImageGenerationResponse.ImageItem item = new ImageGenerationResponse.ImageItem();
            item.setUrl(url);
            item.setFileName(url.substring(url.lastIndexOf('/') + 1));
            images.add(item);
        }
        return images.isEmpty() ? null : writeJson(images);
    }

    private AiModelConfig resolveModelConfig(Long modelConfigId) {
        AiModelConfig modelConfig;
        if (modelConfigId != null) {
            modelConfig = modelConfigService.getById(modelConfigId);
        } else {
            modelConfig = modelConfigService.lambdaQuery()
                    .eq(AiModelConfig::getModelType, "IMAGE")
                    .eq(AiModelConfig::getStatus, 1)
                    .orderByAsc(AiModelConfig::getSort)
                    .last("LIMIT 1")
                    .one();
        }
        if (modelConfig == null || modelConfig.getStatus() == null || modelConfig.getStatus() != 1 || !"IMAGE".equals(modelConfig.getModelType())) {
            throw new ServiceException("图片模型配置不可用");
        }
        if (modelConfig.getApiKey() == null || modelConfig.getApiKey().isBlank()) {
            throw new ServiceException("图片模型 API 密钥未配置");
        }
        return modelConfig;
    }

    private List<String> persistImages(Long taskId, Long userId, String username, List<String> providerUrls) {
        List<String> localUrls = new ArrayList<>();
        for (int i = 0; i < providerUrls.size(); i++) {
            String providerUrl = providerUrls.get(i);
            if (providerUrl == null || providerUrl.isBlank()) continue;
            try {
                ImageBytes image = readImage(providerUrl);
                String ext = extFromMimeType(image.mimeType());
                String fileName = "ai-image-" + taskId + "-" + (i + 1) + "." + ext;
                SysFile file = fileService.upload(new BytesMultipartFile(fileName, image.mimeType(), image.bytes()), userId, username);
                localUrls.add(file.getUrl());
            } catch (Exception e) {
                throw new ServiceException("保存生成图片失败：" + e.getMessage(), e);
            }
        }
        return localUrls;
    }

    private ImageBytes readImage(String url) throws Exception {
        if (url.startsWith("data:")) {
            return readDataUri(url);
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();
        HttpResponse<byte[]> response = HttpClientHolder.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ServiceException("下载生成图片失败：HTTP " + response.statusCode());
        }
        String mimeType = response.headers().firstValue("Content-Type")
                .map(value -> value.split(";")[0].trim())
                .filter(value -> value.startsWith("image/"))
                .orElse("image/png");
        return new ImageBytes(response.body(), mimeType);
    }

    private ImageBytes readDataUri(String dataUri) {
        int comma = dataUri.indexOf(',');
        if (comma < 0) {
            throw new ServiceException("图片 base64 格式异常");
        }
        String meta = dataUri.substring(0, comma);
        String mimeType = "image/png";
        int colon = meta.indexOf(':');
        int semicolon = meta.indexOf(';');
        if (colon >= 0 && semicolon > colon) {
            mimeType = meta.substring(colon + 1, semicolon);
        }
        byte[] bytes = java.util.Base64.getDecoder().decode(dataUri.substring(comma + 1).getBytes(StandardCharsets.UTF_8));
        return new ImageBytes(bytes, mimeType);
    }

    private String extFromMimeType(String mimeType) {
        if (mimeType == null) return "png";
        return switch (mimeType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }

    private List<ImageGenerationResponse.ImageItem> saveAssets(Long userId, Long taskId, String prompt, String modelName, List<String> urls) {
        List<ImageGenerationResponse.ImageItem> images = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            AiAsset asset = new AiAsset();
            asset.setUserId(userId);
            asset.setTaskId(taskId);
            asset.setAssetType("IMAGE");
            asset.setFileName("ai-image-" + taskId + "-" + (i + 1) + ".png");
            asset.setFileUrl(url);
            asset.setThumbnailUrl(url);
            asset.setMimeType("image/png");
            asset.setExt("png");
            asset.setPrompt(prompt);
            asset.setModelName(modelName);
            asset.setStatus(1);
            assetService.saveAsset(asset);

            ImageGenerationResponse.ImageItem item = new ImageGenerationResponse.ImageItem();
            item.setAssetId(asset.getId());
            item.setUrl(url);
            item.setFileName(asset.getFileName());
            images.add(item);
        }
        return images;
    }

    private String shortenError(String message) {
        if (message == null || message.isBlank()) return "未知错误";
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private String writeJson(Object value) {
        try {
            return HttpClientHolder.MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new ServiceException("图片生成结果序列化失败", e);
        }
    }

    private record ImageBytes(byte[] bytes, String mimeType) {
    }

    private record BytesMultipartFile(String fileName, String contentType, byte[] bytes) implements MultipartFile {

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return fileName;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes == null || bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes == null ? 0 : bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            try (InputStream in = getInputStream()) {
                java.nio.file.Files.copy(in, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
