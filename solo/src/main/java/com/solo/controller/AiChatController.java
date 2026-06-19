package com.solo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.entity.AiConversation;
import com.solo.entity.AiMessage;
import com.solo.request.ChatSendRequest;
import com.solo.request.ConversationCreateRequest;
import com.solo.service.AiChatService;
import com.solo.service.AiConversationService;
import com.solo.service.AiMessageService;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.utils.UserHolderUtils;
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
                    emitter.send(SseEmitter.event().name("done").data(Map.of("conversationId", request.getConversationId() == null ? "" : request.getConversationId().toString(), "reply", content)));
                    emitter.complete();
                    return;
                }

                AiChatService.StreamResult result = aiChatService.streamChat(
                        request.getConversationId(),
                        content,
                        userId,
                        request.getModelConfigId(),
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event().name("chunk").data(chunk));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });

                emitter.send(SseEmitter.event().name("done").data(Map.of(
                        "conversationId", String.valueOf(result.conversationId()),
                        "reply", result.reply())));
                emitter.complete();
            } catch (Exception e) {
                log.warn("流式对话失败: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage() == null ? "对话失败" : e.getMessage()));
                } catch (IOException ignore) {
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 获取用户会话列表
     */
    @GetMapping("/conversations")
    @RequirePermission({"ai:conversation:list"})
    public Result<PageResult<AiConversation>> getConversations(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = UserHolderUtils.getCurrentUserId();
        Page<AiConversation> pageResult = conversationService.getUserConversations(userId, page, pageSize);
        return Result.success(new PageResult<>(pageResult.getTotal(), pageResult.getRecords()));
    }

    /**
     * 获取会话消息列表
     */
    @GetMapping("/conversations/{conversationId}/messages")
    @RequirePermission({"ai:conversation:list"})
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
    @RequirePermission({"ai:conversation:create"})
    public Result<AiConversation> createConversation(@RequestBody ConversationCreateRequest request) {
        Long userId = UserHolderUtils.getCurrentUserId();
        AiConversation conversation = conversationService.createConversation(userId, request.getConversationType(), request.getTitle());
        return Result.success(conversation);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/conversations/{conversationId}")
    @RequirePermission({"ai:conversation:delete"})
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
