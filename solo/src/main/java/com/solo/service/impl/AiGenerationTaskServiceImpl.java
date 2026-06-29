package com.solo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.entity.AiGenerationTask;
import com.solo.mapper.AiGenerationTaskMapper;
import com.solo.service.AiGenerationTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI创作任务服务实现类
 */
@Service
public class AiGenerationTaskServiceImpl extends ServiceImpl<AiGenerationTaskMapper, AiGenerationTask> implements AiGenerationTaskService {

    @Override
    @Transactional
    public AiGenerationTask createTextTask(Long userId, Long conversationId, Long messageId, String prompt, String enhancedPrompt, Long modelConfigId, String modelName) {
        AiGenerationTask task = new AiGenerationTask();
        task.setUserId(userId);
        task.setConversationId(conversationId);
        task.setMessageId(messageId);
        task.setTaskType("TEXT");
        task.setPrompt(prompt);
        task.setEnhancedPrompt(enhancedPrompt);
        task.setModelConfigId(modelConfigId);
        task.setModelName(modelName);
        task.setStatus("PENDING");
        task.setStartTime(LocalDateTime.now());
        save(task);
        return task;
    }

    @Override
    @Transactional
    public AiGenerationTask createImageTask(Long userId, Long conversationId, Long messageId, String prompt, Long modelConfigId, String modelName, String modelParams) {
        AiGenerationTask task = new AiGenerationTask();
        task.setUserId(userId);
        task.setConversationId(conversationId);
        task.setMessageId(messageId);
        task.setTaskType("IMAGE");
        task.setPrompt(prompt);
        task.setEnhancedPrompt(prompt);
        task.setModelConfigId(modelConfigId);
        task.setModelName(modelName);
        task.setModelParams(modelParams);
        task.setStatus("PROCESSING");
        task.setStartTime(LocalDateTime.now());
        save(task);
        return task;
    }

    @Override
    @Transactional
    public AiGenerationTask createVideoTask(Long userId, Long conversationId, Long messageId, String prompt, Long modelConfigId, String modelName, String modelParams) {
        AiGenerationTask task = new AiGenerationTask();
        task.setUserId(userId);
        task.setConversationId(conversationId);
        task.setMessageId(messageId);
        task.setTaskType("VIDEO");
        task.setPrompt(prompt);
        task.setEnhancedPrompt(prompt);
        task.setModelConfigId(modelConfigId);
        task.setModelName(modelName);
        task.setModelParams(modelParams);
        task.setStatus("PROCESSING");
        task.setStartTime(LocalDateTime.now());
        save(task);
        return task;
    }

    @Override
    @Transactional
    public void markSuccess(Long taskId, String resultContent, Integer tokens, BigDecimal cost) {
        markSuccess(taskId, "TEXT", resultContent, tokens, cost);
    }

    @Override
    @Transactional
    public void markSuccess(Long taskId, String resultType, String resultContent, Integer tokens, BigDecimal cost) {
        AiGenerationTask task = getById(taskId);
        if (task != null) {
            task.setStatus("SUCCESS");
            task.setResultType(resultType);
            task.setResultContent(resultContent);
            task.setTokens(tokens);
            task.setCost(cost);
            task.setFinishTime(LocalDateTime.now());
            updateById(task);
        }
    }

    @Override
    @Transactional
    public void markFailed(Long taskId, String errorMsg) {
        AiGenerationTask task = getById(taskId);
        if (task != null) {
            task.setStatus("FAILED");
            task.setErrorMsg(errorMsg);
            task.setFinishTime(LocalDateTime.now());
            updateById(task);
        }
    }

    @Override
    public Page<AiGenerationTask> getUserTasks(Long userId, String taskType, String status, Integer page, Integer pageSize) {
        Long pageNum = page != null ? page.longValue() : 1L;
        Long size = pageSize != null ? pageSize.longValue() : 20L;
        Page<AiGenerationTask> pageParam = new Page<>(pageNum, size);

        LambdaQueryWrapper<AiGenerationTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiGenerationTask::getUserId, userId);
        if (taskType != null && !taskType.isEmpty()) {
            wrapper.eq(AiGenerationTask::getTaskType, taskType);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(AiGenerationTask::getStatus, status);
        }
        wrapper.orderByDesc(AiGenerationTask::getCreateTime);

        return page(pageParam, wrapper);
    }
}
