package com.solo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.entity.AiPromptTemplate;

/**
 * Prompt模板服务接口
 */
public interface AiPromptTemplateService extends IService<AiPromptTemplate> {

    /**
     * 根据模板编码获取模板
     */
    AiPromptTemplate getByCode(String templateCode);

    /**
     * 渲染模板内容
     */
    String renderTemplate(String templateCode, java.util.Map<String, Object> variables);
}
