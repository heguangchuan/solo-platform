package com.solo.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solo.ai.entity.AiConversation;
import com.solo.ai.entity.AiMessage;
import com.solo.ai.request.ChatSendRequest;
import com.solo.ai.request.ConversationCreateRequest;
import com.solo.ai.service.AiChatService;
import com.solo.ai.service.AiConversationService;
import com.solo.ai.service.AiMessageService;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.utils.UserHolderUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI对话控制器
 */
@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
@Slf4j
public class AiChatController {

    private final AiChatService aiChatService;
    private final AiConversationService conversationService;
    private final AiMessageService messageService;

    private static final ExecutorService SSE_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread t = new Thread(runnable, "ai-chat-stream");
        t.setDaemon(true);
        return t;
    });

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 发送消息（同步）
     */
    @PostMapping("/send")
    @RequirePermission({"ai:conversation:send"})
    public Result<String> send(@RequestBody ChatSendRequest request) {
        Long userId = UserHolderUtils.getCurrentUserId();

        String response;
        if (request.getTemplateCode() != null && !request.getTemplateCode().isEmpty()) {
            // 使用模板
            response = aiChatService.chatWithTemplate(request.getConversationId(), request.getTemplateCode(), request.getVariables(), userId, request.getModelConfigId());
        } else {
            // 普通对话
            response = aiChatService.chat(request.getConversationId(), request.getContent(), userId, request.getModelConfigId());
        }

        return Result.success(response);
    }

    /**
     * 流式发送消息（SSE）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission({"ai:conversation:send"})
    public SseEmitter stream(@RequestBody ChatSendRequest request) {
        Long userId = UserHolderUtils.getCurrentUserId();
        SseEmitter emitter = new SseEmitter(0L);

        SSE_EXECUTOR.execute(() -> {
            try {
                String content = request.getContent();
                if (request.getTemplateCode() != null && !request.getTemplateCode().isEmpty()) {
                    content = aiChatService.chatWithTemplate(request.getConversationId(), request.getTemplateCode(), request.getVariables(), userId, request.getModelConfigId());
                    emitter.send(SseEmitter.event().name("chunk").data(content));
                    emitter.send(SseEmitter.event().name("done").data(buildDonePayload(
                            request.getConversationId() == null ? "" : request.getConversationId().toString(),
                            content)));
                    emitter.complete();
                    return;
                }

                AiChatService.StreamResult result = aiChatService.streamChat(
                        request.getConversationId(),
                        content,
                        request.getImages(),
                        userId,
                        request.getModelConfigId(),
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event().name("chunk").data(chunk));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });

                emitter.send(SseEmitter.event().name("done").data(buildDonePayload(
                        String.valueOf(result.conversationId()),
                        result.reply())));
                emitter.complete();
            } catch (Exception e) {
                log.warn("流式对话失败: {}", e.getMessage(), e);
                String message = e.getMessage() == null ? "对话失败" : e.getMessage();
                try {
                    emitter.send(SseEmitter.event().name("error").data(message));
                } catch (IOException ignore) {
                }
                // 注意：异常发完后直接 complete()，不要用 completeWithError，
                // 否则 Spring 会把请求重新调度走 ExceptionHandler，最终用 SSE 的
                // Content-Type 写 Result，触发 No converter for ... 错误。
                emitter.complete();
            }
        });

        return emitter;
    }

    /**
     * SSE 的 done 事件数据用 JSON 字符串，避免 Spring 在 text/event-stream 下找不到 Map 的转换器。
     */
    private String buildDonePayload(String conversationId, String reply) {
        try {
            return MAPPER.writeValueAsString(Map.of(
                    "conversationId", conversationId == null ? "" : conversationId,
                    "reply", reply == null ? "" : reply));
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 获取用户会话列表
     */
    @GetMapping("/conversations")
    @RequirePermission({"ai:conversation:list", "ai:imageCreation:list", "ai:videoCreation:list", "ai:creation:list"})
    public Result<PageResult<AiConversation>> getConversations(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String conversationType) {
        Long userId = UserHolderUtils.getCurrentUserId();
        Page<AiConversation> pageResult = conversationService.getUserConversations(userId, conversationType, page, pageSize);
        return Result.success(new PageResult<>(pageResult.getTotal(), pageResult.getRecords()));
    }

    /**
     * 获取会话消息列表
     */
    @GetMapping("/conversations/{conversationId}/messages")
    @RequirePermission({"ai:conversation:list", "ai:imageCreation:list", "ai:videoCreation:list", "ai:creation:list"})
    public Result<List<AiMessage>> getMessages(@PathVariable Long conversationId) {
        Long userId = UserHolderUtils.getCurrentUserId();
        AiConversation conversation = conversationService.getById(conversationId);
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            return Result.success(List.of());
        }
        List<AiMessage> messages = messageService.getConversationMessages(conversationId);
        return Result.success(messages);
    }

    /**
     * 创建新会话
     */
    @PostMapping("/conversations")
    @RequirePermission({"ai:conversation:create", "ai:imageCreation:generate", "ai:videoCreation:generate", "ai:creation:create"})
    public Result<AiConversation> createConversation(@RequestBody ConversationCreateRequest request) {
        Long userId = UserHolderUtils.getCurrentUserId();
        AiConversation conversation = conversationService.createConversation(userId, request.getConversationType(), request.getTitle());
        return Result.success(conversation);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/conversations/{conversationId}")
    @RequirePermission({"ai:conversation:delete", "ai:imageCreation:delete", "ai:videoCreation:delete", "ai:creation:delete"})
    public Result<Void> deleteConversation(@PathVariable Long conversationId) {
        Long userId = UserHolderUtils.getCurrentUserId();
        conversationService.deleteConversation(conversationId, userId);
        return Result.success();
    }

    /**
     * 更新会话标题
     */
    @PutMapping("/conversations/{conversationId}/title")
    @RequirePermission({"ai:conversation:edit"})
    public Result<Void> updateTitle(@PathVariable Long conversationId, @RequestParam String title) {
        conversationService.updateTitle(conversationId, title);
        return Result.success();
    }
}
