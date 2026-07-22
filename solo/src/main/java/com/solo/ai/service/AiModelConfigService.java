package com.solo.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.ai.entity.AiModelConfig;
import com.solo.ai.request.AiModelConfigPageRequest;
import com.solo.ai.request.AiModelConfigRequest;

import java.util.List;

/**
 * AI模型配置服务接口
 */
public interface AiModelConfigService extends IService<AiModelConfig> {

    /**
     * 分页查询
     */
    Page<AiModelConfig> pageQuery(AiModelConfigPageRequest req);

    /**
     * 新增配置
     */
    void createConfig(AiModelConfigRequest request);

    /**
     * 修改配置
     */
    void updateConfig(Long id, AiModelConfigRequest request);

    /**
     * 获取启用的默认文本模型
     */
    AiModelConfig getDefaultTextModel();

    /**
     * 根据模型名称获取配置
     */
    AiModelConfig getByModelName(String modelName);

    /**
     * 获取所有启用的配置列表
     */
    List<AiModelConfig> getEnabledList();
}
