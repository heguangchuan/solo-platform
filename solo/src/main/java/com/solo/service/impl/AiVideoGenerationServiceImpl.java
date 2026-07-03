package com.solo.service.impl;

import com.solo.common.ServiceException;
import com.solo.entity.AiAsset;
import com.solo.entity.AiConversation;
import com.solo.entity.AiGenerationTask;
import com.solo.entity.AiMessage;
import com.solo.entity.AiModelConfig;
import com.solo.entity.AiProviderTask;
import com.solo.entity.SysFile;
import com.solo.llm.AttachmentFetcher;
import com.solo.llm.HttpClientHolder;
import com.solo.llm.VideoGenerationClient;
import com.solo.llm.VideoGenerationClientRegistry;
import com.solo.llm.VideoGenerationQueryResult;
import com.solo.llm.VideoGenerationSubmitResult;
import com.solo.request.VideoGenerationRequest;
import com.solo.response.VideoGenerationResponse;
import com.solo.service.AiAssetService;
import com.solo.service.AiConversationService;
import com.solo.service.AiGenerationTaskService;
import com.solo.service.AiMessageService;
import com.solo.service.AiModelConfigService;
import com.solo.service.AiProviderTaskService;
import com.solo.service.AiVideoGenerationService;
import com.solo.service.SysFileService;
import com.solo.service.SysUserService;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 视频生成服务实现。
 */
@Service
@RequiredArgsConstructor
public class AiVideoGenerationServiceImpl implements AiVideoGenerationService {

    private static final int POLL_LIMIT = 10;

    private final AiModelConfigService modelConfigService;
    private final AiGenerationTaskService generationTaskService;
    private final AiConversationService conversationService;
    private final AiMessageService messageService;
    private final AiProviderTaskService providerTaskService;
    private final AiAssetService assetService;
    private final SysFileService fileService;
    private final SysUserService sysUserService;
    private final VideoGenerationClientRegistry videoClientRegistry;
    @Qualifier("aiTaskExecutor")
    private final ThreadPoolTaskExecutor aiTaskExecutor;

    @Override
    public VideoGenerationResponse generate(Long userId, VideoGenerationRequest request) {
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new ServiceException("请输入视频描述");
        }
        AiModelConfig modelConfig = resolveModelConfig(request.getModelConfigId());
        normalizeRequest(request);
        List<String> originalImages = copyImages(request.getImage());
        convertReferenceImagesToBase64(request);
        AiConversation conversation = resolveConversation(userId, request.getConversationId(), request.getPrompt(), modelConfig.getId());
        String userAttachments = serializeReferenceVideos(originalImages);
        AiMessage userMessage = messageService.addUserMessage(conversation.getId(), request.getPrompt(), userAttachments);
        AiGenerationTask task = generationTaskService.createVideoTask(userId, conversation.getId(), userMessage.getId(), request.getPrompt(), modelConfig.getId(), modelConfig.getModelName(), modelConfig.getModelParams());
        String username = sysUserService.getById(userId).getUsername();
        aiTaskExecutor.execute(() -> submitProviderTask(task.getId(), userId, username, conversation.getId(), request, modelConfig));

        VideoGenerationResponse response = new VideoGenerationResponse();
        response.setConversationId(conversation.getId());
        response.setTaskId(task.getId());
        response.setModelName(modelConfig.getModelName());
        response.setStatus(task.getStatus());
        return response;
    }

    private void submitProviderTask(Long taskId, Long userId, String username, Long conversationId, VideoGenerationRequest request, AiModelConfig modelConfig) {
        try {
            VideoGenerationClient client = videoClientRegistry.resolve(modelConfig.getProvider());
            VideoGenerationSubmitResult submit = client.submit(modelConfig, request);
            AiProviderTask providerTask = new AiProviderTask();
            providerTask.setGenerationTaskId(taskId);
            providerTask.setConversationId(conversationId);
            providerTask.setUserId(userId);
            providerTask.setUsername(username);
            providerTask.setTaskType("VIDEO");
            providerTask.setProvider(modelConfig.getProvider());
            providerTask.setModelName(modelConfig.getModelName());
            providerTask.setModelConfigId(modelConfig.getId());
            providerTask.setProviderTaskId(submit.taskId());
            providerTask.setProviderVideoId(submit.videoId());
            providerTask.setProviderStatus(submit.status());
            providerTask.setProgress(0);
            providerTask.setSeconds(submit.seconds());
            providerTask.setSize(submit.size());
            providerTask.setStartTime(LocalDateTime.now());
            providerTask.setNextCheckTime(LocalDateTime.now().plusSeconds(5));
            providerTaskService.save(providerTask);
        } catch (Exception e) {
            generationTaskService.markFailed(taskId, shortenError(e.getMessage()));
        }
    }

    public void pollVideoProviderTasks() {
        List<AiProviderTask> tasks = providerTaskService.listPendingVideoTasks(POLL_LIMIT);
        for (AiProviderTask providerTask : tasks) {
            try {
                pollOneProviderTask(providerTask);
            } catch (Exception e) {
                providerTask.setErrorMsg(shortenError(e.getMessage()));
                providerTask.setNextCheckTime(LocalDateTime.now().plusMinutes(2));
                providerTaskService.updateById(providerTask);
            }
        }
    }

    private void pollOneProviderTask(AiProviderTask providerTask) {
        AiModelConfig modelConfig = modelConfigService.getById(providerTask.getModelConfigId());
        if (modelConfig == null) {
            throw new ServiceException("视频模型配置不存在");
        }
        VideoGenerationClient client = videoClientRegistry.resolve(modelConfig.getProvider());
        VideoGenerationQueryResult query = client.query(modelConfig, providerTask.getProviderTaskId(), providerTask.getProviderVideoId());
        providerTask.setProviderStatus(query.status());
        providerTask.setProgress(query.progress());
        providerTask.setResultUrl(query.videoUrl());
        providerTask.setErrorMsg(query.error());
        providerTask.setSeconds(query.seconds());
        providerTask.setSize(query.size());

        if ("completed".equalsIgnoreCase(query.status())) {
            String localUrl;
            try {
                localUrl = persistVideo(providerTask.getGenerationTaskId(), providerTask.getUserId(), providerTask.getUsername(), query.videoUrl());
            } catch (Exception e) {
                throw new ServiceException(e.getMessage(), e);
            }
            VideoGenerationResponse response = saveAsset(providerTask.getUserId(), providerTask.getGenerationTaskId(), loadTaskPrompt(providerTask.getGenerationTaskId()), providerTask.getModelName(), localUrl);
            String resultContent = writeJson(List.of(response));
            messageService.addAssistantVideoMessage(providerTask.getConversationId(), "已生成视频", resultContent, providerTask.getModelName());
            generationTaskService.markSuccess(providerTask.getGenerationTaskId(), "URL", resultContent, null, null);
            providerTask.setFinishTime(LocalDateTime.now());
            providerTaskService.updateById(providerTask);
        } else if ("failed".equalsIgnoreCase(query.status())) {
            providerTask.setFinishTime(LocalDateTime.now());
            providerTaskService.updateById(providerTask);
            generationTaskService.markFailed(providerTask.getGenerationTaskId(), query.error() == null || query.error().isBlank() ? "视频生成失败" : query.error());
        } else {
            providerTask.setNextCheckTime(LocalDateTime.now().plusSeconds(30));
            providerTaskService.updateById(providerTask);
        }
    }

    private String loadTaskPrompt(Long taskId) {
        AiGenerationTask task = generationTaskService.getById(taskId);
        return task == null ? "" : task.getPrompt();
    }

    private void normalizeRequest(VideoGenerationRequest request) {
        if (request.getHeight() == null) request.setHeight(768);
        if (request.getWidth() == null) request.setWidth(1152);
        if (request.getNumFrames() == null) request.setNumFrames(121);
        if (request.getFrameRate() == null) request.setFrameRate(24);
        if (request.getNumFrames() > 441 || (request.getNumFrames() - 1) % 8 != 0) {
            throw new ServiceException("num_frames 必须小于等于 441 且满足 8n + 1");
        }
        if (request.getFrameRate() < 1 || request.getFrameRate() > 60) {
            throw new ServiceException("frame_rate 必须在 1 到 60 之间");
        }
    }

    private List<String> copyImages(List<String> images) {
        if (images == null || images.isEmpty()) return List.of();
        return images.stream()
                .filter(url -> url != null && !url.isBlank())
                .toList();
    }

    private void convertReferenceImagesToBase64(VideoGenerationRequest request) {
        List<String> images = copyImages(request.getImage());
        if (images.isEmpty()) return;
        List<String> dataUris = new ArrayList<>();
        for (String image : images) {
            String dataUri = AttachmentFetcher.toDataUri(image, null);
            if (dataUri == null) {
                throw new ServiceException("参考图片转 base64 失败");
            }
            dataUris.add(stripDataUriPrefix(dataUri));
        }
        request.setImage(dataUris);
    }

    private String stripDataUriPrefix(String dataUri) {
        int comma = dataUri.indexOf(',');
        return comma >= 0 ? dataUri.substring(comma + 1) : dataUri;
    }

    private AiConversation resolveConversation(Long userId, Long conversationId, String prompt, Long modelConfigId) {
        if (conversationId != null) {
            AiConversation conversation = conversationService.getById(conversationId);
            if (conversation == null || !userId.equals(conversation.getUserId())
                    || !isCreationConversation(conversation.getConversationType(), "VIDEO")) {
                throw new ServiceException("会话不存在");
            }
            if (!modelConfigId.equals(conversation.getModelConfigId())) {
                conversation.setModelConfigId(modelConfigId);
                conversationService.updateById(conversation);
            }
            return conversation;
        }
        AiConversation conversation = conversationService.createConversation(userId, "VIDEO", generateTitle(prompt));
        conversation.setModelConfigId(modelConfigId);
        conversationService.updateById(conversation);
        return conversation;
    }

    private String generateTitle(String prompt) {
        if (prompt == null || prompt.isBlank()) return "视频创作";
        return prompt.length() <= 20 ? prompt : prompt.substring(0, 20) + "...";
    }

    /** 单轮页会话类型必须精确匹配，统一创作页 MIXED 会话也可复用本链路 */
    private boolean isCreationConversation(String conversationType, String own) {
        return own.equals(conversationType) || "MIXED".equals(conversationType);
    }

    private String serializeReferenceVideos(List<String> urls) {
        if (urls == null || urls.isEmpty()) return null;
        List<VideoGenerationResponse> items = new ArrayList<>();
        for (String url : urls) {
            if (url == null || url.isBlank()) continue;
            VideoGenerationResponse item = new VideoGenerationResponse();
            item.setVideoUrl(url);
            item.setFileName(url.substring(url.lastIndexOf('/') + 1));
            items.add(item);
        }
        return items.isEmpty() ? null : writeJson(items);
    }

    private AiModelConfig resolveModelConfig(Long modelConfigId) {
        AiModelConfig modelConfig;
        if (modelConfigId != null) {
            modelConfig = modelConfigService.getById(modelConfigId);
        } else {
            modelConfig = modelConfigService.lambdaQuery()
                    .eq(AiModelConfig::getModelType, "VIDEO")
                    .eq(AiModelConfig::getStatus, 1)
                    .orderByAsc(AiModelConfig::getSort)
                    .last("LIMIT 1")
                    .one();
        }
        if (modelConfig == null || modelConfig.getStatus() == null || modelConfig.getStatus() != 1 || !"VIDEO".equals(modelConfig.getModelType())) {
            throw new ServiceException("视频模型配置不可用");
        }
        if (modelConfig.getApiKey() == null || modelConfig.getApiKey().isBlank()) {
            throw new ServiceException("视频模型 API 密钥未配置");
        }
        return modelConfig;
    }

    private String persistVideo(Long taskId, Long userId, String username, String providerUrl) throws Exception {
        if (providerUrl == null || providerUrl.isBlank()) throw new ServiceException("视频结果为空");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(providerUrl))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
        HttpResponse<byte[]> response = HttpClientHolder.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ServiceException("下载生成视频失败：HTTP " + response.statusCode());
        }
        String mimeType = response.headers().firstValue("Content-Type")
                .map(value -> value.split(";")[0].trim())
                .filter(value -> value.startsWith("video/"))
                .orElse("video/mp4");
        String ext = "video/webm".equalsIgnoreCase(mimeType) ? "webm" : "mp4";
        String fileName = "ai-video-" + taskId + "." + ext;
        SysFile file = fileService.upload(new BytesMultipartFile(fileName, mimeType, response.body()), userId, username);
        return file.getUrl();
    }

    private VideoGenerationResponse saveAsset(Long userId, Long taskId, String prompt, String modelName, String url) {
        AiAsset asset = new AiAsset();
        asset.setAssetType("VIDEO");
        asset.setSource("MODEL_GENERATE");
        asset.setFileName("ai-video-" + taskId + ".mp4");
        asset.setFileUrl(url);
        assetService.saveAsset(asset);

        VideoGenerationResponse response = new VideoGenerationResponse();
        response.setAssetId(asset.getId());
        response.setVideoUrl(url);
        response.setFileName(asset.getFileName());
        return response;
    }

    private String shortenError(String message) {
        if (message == null || message.isBlank()) return "未知错误";
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private String writeJson(Object value) {
        try {
            return HttpClientHolder.MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new ServiceException("视频生成结果序列化失败", e);
        }
    }

    private record BytesMultipartFile(String fileName, String contentType, byte[] bytes) implements MultipartFile {
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return fileName; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return bytes == null || bytes.length == 0; }
        @Override public long getSize() { return bytes == null ? 0 : bytes.length; }
        @Override public byte[] getBytes() { return bytes; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            try (InputStream in = getInputStream()) {
                java.nio.file.Files.copy(in, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
