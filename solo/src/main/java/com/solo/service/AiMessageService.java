package com.solo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.entity.AiMessage;

import java.util.List;

/**
 * AI消息服务接口
 */
public interface AiMessageService extends IService<AiMessage> {

    /**
     * 添加用户消息
     */
    AiMessage addUserMessage(Long conversationId, String content);

    /**
     * 添加用户消息（含附件）
     */
    AiMessage addUserMessage(Long conversationId, String content, String attachmentsJson);

    /**
     * 添加助手消息
     */
    AiMessage addAssistantMessage(Long conversationId, String content, String modelName, Integer tokens);

    /**
     * 添加助手图片消息
     */
    AiMessage addAssistantImageMessage(Long conversationId, String content, String attachmentsJson, String modelName);

    /**
     * 添加系统消息
     */
    AiMessage addSystemMessage(Long conversationId, String content);

    /**
     * 获取会话的所有消息（按时间正序）
     */
    List<AiMessage> getConversationMessages(Long conversationId);

    /**
     * 获取会话的最近 N 条消息（用于上下文）
     */
    List<AiMessage> getRecentMessages(Long conversationId, int limit);
}
