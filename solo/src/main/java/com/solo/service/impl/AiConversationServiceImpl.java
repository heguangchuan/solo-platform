package com.solo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.entity.AiConversation;
import com.solo.entity.AiGenerationTask;
import com.solo.entity.AiMessage;
import com.solo.mapper.AiConversationMapper;
import com.solo.mapper.AiGenerationTaskMapper;
import com.solo.mapper.AiMessageMapper;
import com.solo.service.AiConversationService;
import com.solo.common.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * AI会话服务实现类
 */
@Service
@RequiredArgsConstructor
public class AiConversationServiceImpl extends ServiceImpl<AiConversationMapper, AiConversation> implements AiConversationService {

    /**
     * 会话删除时直接用 Mapper 级联清理消息和任务，避免与依赖会话服务的 Service 形成循环依赖。
     */
    private final AiMessageMapper messageMapper;
    private final AiGenerationTaskMapper generationTaskMapper;

    @Override
    @Transactional
    public AiConversation createConversation(Long userId, String conversationType, String title) {
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setConversationType(conversationType != null ? conversationType : "CHAT");
        conversation.setTitle(title != null ? title : "新对话");
        conversation.setMessageCount(0);
        save(conversation);
        return conversation;
    }

    @Override
    public Page<AiConversation> getUserConversations(Long userId, Integer page, Integer pageSize) {
        Long pageNum = page != null ? page.longValue() : 1L;
        Long size = pageSize != null ? pageSize.longValue() : 20L;
        Page<AiConversation> pageParam = new Page<>(pageNum, size);

        LambdaQueryWrapper<AiConversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiConversation::getUserId, userId)
                .orderByDesc(AiConversation::getLastMessageTime)
                .orderByDesc(AiConversation::getCreateTime);

        return page(pageParam, wrapper);
    }

    @Override
    @Transactional
    public void updateTitle(Long conversationId, String title) {
        AiConversation conversation = getById(conversationId);
        if (conversation == null) {
            throw new ServiceException("会话不存在");
        }
        conversation.setTitle(title);
        updateById(conversation);
    }

    @Override
    @Transactional
    public void updateLastMessageTime(Long conversationId) {
        AiConversation conversation = getById(conversationId);
        if (conversation != null) {
            conversation.setLastMessageTime(LocalDateTime.now());
            conversation.setMessageCount(conversation.getMessageCount() + 1);
            updateById(conversation);
        }
    }

    @Override
    @Transactional
    public void deleteConversation(Long conversationId, Long userId) {
        AiConversation conversation = getById(conversationId);
        if (conversation == null) {
            throw new ServiceException("会话不存在");
        }
        if (!conversation.getUserId().equals(userId)) {
            throw new ServiceException("无权删除此会话");
        }
        // 同时清理会话下的消息和创作任务（均为 @TableLogic 逻辑删除）
        messageMapper.delete(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId));
        generationTaskMapper.delete(new LambdaQueryWrapper<AiGenerationTask>()
                .eq(AiGenerationTask::getConversationId, conversationId));
        removeById(conversationId);
    }
}
