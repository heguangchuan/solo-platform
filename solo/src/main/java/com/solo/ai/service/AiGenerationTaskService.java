package com.solo.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.ai.entity.AiGenerationTask;

/**
 * AI创作任务服务接口
 */
public interface AiGenerationTaskService extends IService<AiGenerationTask> {

    /**
     * 创建文本生成任务
     */
    AiGenerationTask createTextTask(Long userId, Long conversationId, Long messageId, String prompt, String enhancedPrompt, Long modelConfigId, String modelName);

    /**
     * 创建图片生成任务
     */
    AiGenerationTask createImageTask(Long userId, Long conversationId, Long messageId, String prompt, Long modelConfigId, String modelName, String modelParams);

    /**
     * 创建视频生成任务
     */
    AiGenerationTask createVideoTask(Long userId, Long conversationId, Long messageId, String prompt, Long modelConfigId, String modelName, String modelParams);

    /**
     * 更新任务状态为成功
     */
    void markSuccess(Long taskId, String resultContent, Integer tokens, java.math.BigDecimal cost);

    /**
     * 更新任务状态为成功
     */
    void markSuccess(Long taskId, String resultType, String resultContent, Integer tokens, java.math.BigDecimal cost);

    /**
     * 更新任务状态为失败
     */
    void markFailed(Long taskId, String errorMsg);

    /**
     * 获取用户任务列表
     */
    Page<AiGenerationTask> getUserTasks(Long userId, String taskType, String status, Integer page, Integer pageSize);
}
