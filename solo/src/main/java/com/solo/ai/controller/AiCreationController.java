package com.solo.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solo.aop.RequirePermission;
import com.solo.ai.request.ChatSendRequest;
import com.solo.ai.service.AiCreationService;
import com.solo.common.utils.UserHolderUtils;
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
 * 统一创作端点（对话 / 生图 / 生视频 共用一个流式入口）
 *
 * <p>前端"AI 助手"页希望在一个会话里既能和模型对话，又能直接让它画图 / 生视频，
 * 这套接口就是为了这种混合场景而设计：用户输入一句话，模型先回复文字，
 * 如果觉得需要生成素材，会一并把生图 / 生视频任务 ID 推回来。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /api/ai/creation/stream} — 统一创作流式端点（SSE）。
 *       后端根据会话上下文自动判定是 {@code CHAT} / {@code IMAGE} / {@code VIDEO} 任务；
 *       对话走 LLM 流式输出；生图 / 生视频会创建生成任务并通过 {@code task-created} 事件回包。</li>
 * </ul>
 *
 * <h3>SSE 事件清单</h3>
 * <ul>
 *   <li>{@code chunk} — 文本增量（对话）或 占位提示（生图 / 生视频：{@code "正在生成图片..."} 之类的中文提示）</li>
 *   <li>{@code task-created} — 生图 / 生视频任务已创建；data 是 JSON：
 *       {@code {"taskId":"...","type":"IMAGE|VIDEO","conversationId":"..."}}，
 *       前端拿到 {@code taskId} 后调 {@code GET /api/ai/task/{taskId}} 轮询进度</li>
 *   <li>{@code done} — 结束；data 是 JSON：{@code {"conversationId":"...","type":"...","reply":"..."}}</li>
 *   <li>{@code error} — 异常；data 是错误描述字符串</li>
 * </ul>
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
     * 统一创作流式端点（SSE，前端用 EventSource 或 fetch 接收）
     *
     * <p>请求体同 {@code /api/ai/chat/send}：{@code conversationId} / {@code content} / {@code modelConfigId} / {@code images}。
     * {@code conversationId} 必填且必须是 {@code conversationType=CREATION} 的会话，
     * 否则无法触发生图 / 生视频分支。</p>
     *
     * <p>前端轮询建议：收到 {@code task-created} 事件后，每 3~5 秒调一次
     * {@code GET /api/ai/task/{taskId}}，直到 {@code status} 变为 {@code SUCCESS} 或 {@code FAILED}。</p>
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
