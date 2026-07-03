package com.solo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solo.aop.RequirePermission;
import com.solo.request.ChatSendRequest;
import com.solo.service.AiCreationService;
import com.solo.utils.UserHolderUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 统一创作控制器：一个多轮会话内路由 对话 / 生图 / 生视频。
 */
@RestController
@RequestMapping("/api/ai/creation")
@RequiredArgsConstructor
@Slf4j
public class AiCreationController {

    private final AiCreationService creationService;

    private static final ExecutorService SSE_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread t = new Thread(runnable, "ai-creation-stream");
        t.setDaemon(true);
        return t;
    });

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 统一创作流式端点（SSE）。
     * 事件：chunk(文本增量/占位) / task-created(生成任务) / done / error
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission({"ai:creation:send"})
    public SseEmitter stream(@RequestBody ChatSendRequest request) {
        Long userId = UserHolderUtils.getCurrentUserId();
        SseEmitter emitter = new SseEmitter(0L);

        SSE_EXECUTOR.execute(() -> {
            try {
                AiCreationService.CreationResult result = creationService.send(
                        request.getConversationId(),
                        request.getContent(),
                        extractImageUrls(request),
                        userId,
                        request.getModelConfigId(),
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event().name("chunk").data(chunk));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });

                if (!"CHAT".equals(result.type()) && result.taskId() != null) {
                    emitter.send(SseEmitter.event().name("task-created").data(buildTaskPayload(result)));
                }
                emitter.send(SseEmitter.event().name("done").data(buildDonePayload(result)));
                emitter.complete();
            } catch (Exception e) {
                log.warn("统一创作失败: {}", e.getMessage(), e);
                String message = e.getMessage() == null ? "创作失败" : e.getMessage();
                try {
                    emitter.send(SseEmitter.event().name("error").data(message));
                } catch (IOException ignore) {
                }
                emitter.complete();
            }
        });

        return emitter;
    }

    private String buildTaskPayload(AiCreationService.CreationResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", result.taskId() == null ? "" : result.taskId().toString());
        payload.put("type", result.type());
        payload.put("conversationId", result.conversationId() == null ? "" : result.conversationId().toString());
        return writeJson(payload);
    }

    private java.util.List<String> extractImageUrls(ChatSendRequest request) {
        if (request.getImages() == null || request.getImages().isEmpty()) {
            return null;
        }
        java.util.List<String> urls = new java.util.ArrayList<>();
        for (ChatSendRequest.AttachmentInput input : request.getImages()) {
            if (input != null && input.getUrl() != null && !input.getUrl().isBlank()) {
                urls.add(input.getUrl());
            }
        }
        return urls.isEmpty() ? null : urls;
    }

    private String buildDonePayload(AiCreationService.CreationResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", result.conversationId() == null ? "" : result.conversationId().toString());
        payload.put("type", result.type());
        payload.put("reply", result.reply() == null ? "" : result.reply());
        return writeJson(payload);
    }

    private String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
