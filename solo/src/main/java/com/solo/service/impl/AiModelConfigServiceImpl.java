package com.solo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.entity.AiModelConfig;
import com.solo.mapper.AiModelConfigMapper;
import com.solo.service.AiModelConfigService;
import org.springframework.stereotype.Service;

/**
 * AI模型配置服务实现类
 */
@Service
public class AiModelConfigServiceImpl extends ServiceImpl<AiModelConfigMapper, AiModelConfig> implements AiModelConfigService {

    @Override
    public AiModelConfig getDefaultTextModel() {
        return lambdaQuery()
                .eq(AiModelConfig::getModelType, "TEXT")
                .eq(AiModelConfig::getStatus, 1)
                .orderByAsc(AiModelConfig::getSort)
                .last("LIMIT 1")
                .one();
    }

    @Override
    public AiModelConfig getByModelName(String modelName) {
        return lambdaQuery()
                .eq(AiModelConfig::getModelName, modelName)
                .eq(AiModelConfig::getStatus, 1)
                .one();
    }
}
