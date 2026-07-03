package com.solo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.solo.entity.AiMessage;
import com.solo.llm.HttpClientHolder;
import com.solo.mapper.AiMessageMapper;
import com.solo.service.AiConversationService;
import com.solo.service.AiMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * AI消息服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiMessageServiceImpl extends ServiceImpl<AiMessageMapper, AiMessage> implements AiMessageService {

    private final AiConversationService conversationService;

    @Override
    @Transactional
    public AiMessage addUserMessage(Long conversationId, String content) {
        return addUserMessage(conversationId, content, null);
    }

    @Override
    @Transactional
    public AiMessage addUserMessage(Long conversationId, String content, String attachmentsJson) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setRole("USER");
        message.setContent(content);
        message.setMessageType("TEXT");
        if (attachmentsJson != null && !attachmentsJson.isBlank()) {
            message.setAttachments(attachmentsJson);
        }
        save(message);

        conversationService.updateLastMessageTime(conversationId);
        return message;
    }

    @Override
    @Transactional
    public AiMessage addAssistantMessage(Long conversationId, String content, String modelName, Integer tokens) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setRole("ASSISTANT");
        message.setContent(content);
        message.setMessageType("TEXT");
        message.setModelName(modelName);
        message.setTokens(tokens);
        save(message);

        conversationService.updateLastMessageTime(conversationId);
        return message;
    }

    @Override
    @Transactional
    public AiMessage addAssistantImageMessage(Long conversationId, String content, String attachmentsJson, String modelName) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setRole("ASSISTANT");
        message.setContent(content);
        message.setMessageType("IMAGE");
        message.setModelName(modelName);
        message.setAttachments(attachmentsJson);
        save(message);

        conversationService.updateLastMessageTime(conversationId);
        return message;
    }

    @Override
    @Transactional
    public AiMessage addAssistantVideoMessage(Long conversationId, String content, String attachmentsJson, String modelName) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setRole("ASSISTANT");
        message.setContent(content);
        message.setMessageType("VIDEO");
        message.setModelName(modelName);
        message.setAttachments(attachmentsJson);
        save(message);

        conversationService.updateLastMessageTime(conversationId);
        return message;
    }

    @Override
    @Transactional
    public AiMessage addSystemMessage(Long conversationId, String content) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setRole("SYSTEM");
        message.setContent(content);
        message.setMessageType("TEXT");
        save(message);
        return message;
    }

    @Override
    public List<AiMessage> getConversationMessages(Long conversationId) {
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getCreateTime);
        return list(wrapper);
    }

    @Override
    public List<AiMessage> getRecentMessages(Long conversationId, int limit) {
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMessage::getConversationId, conversationId)
                .orderByDesc(AiMessage::getCreateTime)
                .last("LIMIT " + limit);
        List<AiMessage> messages = list(wrapper);
        Collections.reverse(messages);
        return messages;
    }

    @Override
    public String findLastGeneratedImageUrl(Long conversationId) {
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMessage::getConversationId, conversationId)
                .eq(AiMessage::getRole, "ASSISTANT")
                .eq(AiMessage::getMessageType, "IMAGE")
                .isNotNull(AiMessage::getAttachments)
                .orderByDesc(AiMessage::getCreateTime)
                .last("LIMIT 1");
        AiMessage message = getOne(wrapper, false);
        if (message == null || message.getAttachments() == null || message.getAttachments().isBlank()) {
            return null;
        }
        try {
            JsonNode arr = HttpClientHolder.MAPPER.readTree(message.getAttachments());
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    String url = node.path("url").asText("");
                    if (!url.isBlank()) return url;
                }
            }
        } catch (Exception e) {
            log.debug("解析历史图片附件失败：{}", e.getMessage());
        }
        return null;
    }
}
