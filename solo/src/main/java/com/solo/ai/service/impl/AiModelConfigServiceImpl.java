package com.solo.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.ai.entity.AiModelConfig;
import com.solo.ai.mapper.AiModelConfigMapper;
import com.solo.ai.request.AiModelConfigPageRequest;
import com.solo.ai.request.AiModelConfigRequest;
import com.solo.ai.service.AiModelConfigService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI模型配置服务实现类
 */
@Service
public class AiModelConfigServiceImpl extends ServiceImpl<AiModelConfigMapper, AiModelConfig> implements AiModelConfigService {

    @Override
    public Page<AiModelConfig> pageQuery(AiModelConfigPageRequest req) {
        Long page = req.getPage() != null ? req.getPage() : 1L;
        Long pageSize = req.getPageSize() != null ? req.getPageSize() : 20L;
        Page<AiModelConfig> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<>();
        if (req.getProvider() != null && !req.getProvider().isBlank()) {
            wrapper.like(AiModelConfig::getProvider, req.getProvider());
        }
        if (req.getModelType() != null && !req.getModelType().isBlank()) {
            wrapper.eq(AiModelConfig::getModelType, req.getModelType());
        }
        if (req.getModelName() != null && !req.getModelName().isBlank()) {
            wrapper.like(AiModelConfig::getModelName, req.getModelName());
        }
        if (req.getStatus() != null) {
            wrapper.eq(AiModelConfig::getStatus, req.getStatus());
        }
        wrapper.orderByAsc(AiModelConfig::getSort).orderByDesc(AiModelConfig::getCreateTime);
        return page(pageParam, wrapper);
    }

    @Override
    public void createConfig(AiModelConfigRequest request) {
        if (request.getApiKey() == null || request.getApiKey().isBlank()) {
            throw new ServiceException("API密钥不能为空");
        }
        AiModelConfig config = new AiModelConfig();
        config.setProvider(request.getProvider());
        config.setModelName(request.getModelName());
        config.setModelType(request.getModelType());
        config.setApiKey(request.getApiKey());
        config.setBaseUrl(request.getBaseUrl());
        config.setModelParams(request.getModelParams());
        config.setSupportVision(request.getSupportVision());
        config.setStatus(request.getStatus());
        config.setSort(request.getSort());
        save(config);
    }

    @Override
    public void updateConfig(Long id, AiModelConfigRequest request) {
        AiModelConfig config = getById(id);
        if (config == null) {
            throw new ServiceException("模型配置不存在");
        }
        if (request.getProvider() != null) config.setProvider(request.getProvider());
        if (request.getModelName() != null) config.setModelName(request.getModelName());
        if (request.getModelType() != null) config.setModelType(request.getModelType());
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) config.setApiKey(request.getApiKey());
        if (request.getBaseUrl() != null) config.setBaseUrl(request.getBaseUrl());
        if (request.getModelParams() != null) config.setModelParams(request.getModelParams());
        if (request.getSupportVision() != null) config.setSupportVision(request.getSupportVision());
        if (request.getStatus() != null) config.setStatus(request.getStatus());
        if (request.getSort() != null) config.setSort(request.getSort());
        updateById(config);
    }

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

    @Override
    public List<AiModelConfig> getEnabledList() {
        return lambdaQuery()
                .eq(AiModelConfig::getStatus, 1)
                .orderByAsc(AiModelConfig::getSort)
                .list();
    }
}
