package com.solo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.entity.AiProviderTask;

import java.util.List;

/**
 * AI供应商异步任务服务接口。
 */
public interface AiProviderTaskService extends IService<AiProviderTask> {

    List<AiProviderTask> listPendingVideoTasks(int limit);
}
