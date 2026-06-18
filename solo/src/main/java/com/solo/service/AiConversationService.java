package com.solo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.entity.AiConversation;

/**
 * AI会话服务接口
 */
public interface AiConversationService extends IService<AiConversation> {

    /**
     * 创建新会话
     */
    AiConversation createConversation(Long userId, String conversationType, String title);

    /**
     * 获取用户会话列表
     */
    Page<AiConversation> getUserConversations(Long userId, Integer page, Integer pageSize);

    /**
     * 更新会话标题
     */
    void updateTitle(Long conversationId, String title);

    /**
     * 更新最后消息时间
     */
    void updateLastMessageTime(Long conversationId);

    /**
     * 删除会话
     */
    void deleteConversation(Long conversationId, Long userId);
}
