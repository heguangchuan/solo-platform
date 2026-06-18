package com.solo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.entity.AiModelConfig;

/**
 * AI模型配置服务接口
 */
public interface AiModelConfigService extends IService<AiModelConfig> {

    /**
     * 获取启用的默认文本模�?     */
    AiModelConfig getDefaultTextModel();

    /**
     * 根据模型名称获取配置
     */
    AiModelConfig getByModelName(String modelName);
}
