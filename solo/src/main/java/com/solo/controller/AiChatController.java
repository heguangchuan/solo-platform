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
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI对话控制器
 */
@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;
    private final AiConversationService conversationService;
    private final AiMessageService messageService;

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
