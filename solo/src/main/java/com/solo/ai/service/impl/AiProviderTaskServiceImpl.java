package com.solo.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.ai.entity.AiProviderTask;
import com.solo.ai.mapper.AiProviderTaskMapper;
import com.solo.ai.service.AiProviderTaskService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI供应商异步任务服务实现类。
 */
@Service
public class AiProviderTaskServiceImpl extends ServiceImpl<AiProviderTaskMapper, AiProviderTask> implements AiProviderTaskService {

    @Override
    public List<AiProviderTask> listPendingVideoTasks(int limit) {
        return list(new LambdaQueryWrapper<AiProviderTask>()
                .eq(AiProviderTask::getTaskType, "VIDEO")
                .and(wrapper -> wrapper
                        .isNull(AiProviderTask::getProviderStatus)
                        .or()
                        .notIn(AiProviderTask::getProviderStatus, "completed", "failed"))
                .le(AiProviderTask::getNextCheckTime, LocalDateTime.now())
                .orderByAsc(AiProviderTask::getNextCheckTime)
                .last("LIMIT " + limit));
    }
}
