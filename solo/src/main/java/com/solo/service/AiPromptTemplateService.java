package com.solo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.entity.AiPromptTemplate;
import com.solo.request.AiPromptTemplatePageRequest;
import com.solo.request.AiPromptTemplateRequest;

import java.util.List;
import java.util.Map;

/**
 * 提示词模板服务接口
 */
public interface AiPromptTemplateService extends IService<AiPromptTemplate> {

    /**
     * 分页查询
     */
    Page<AiPromptTemplate> pageQuery(AiPromptTemplatePageRequest req);

    /**
     * 新增模板
     */
    void createTemplate(AiPromptTemplateRequest request);

    /**
     * 修改模板
     */
    void updateTemplate(Long id, AiPromptTemplateRequest request);

    /**
     * 根据模板编码获取模板
     */
    AiPromptTemplate getByCode(String templateCode);

    /**
     * 渲染模板内容
     */
    String renderTemplate(String templateCode, Map<String, Object> variables);

    /**
     * 获取启用的模板列表
     */
    List<AiPromptTemplate> getEnabledList();
}
