package com.solo.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.ai.entity.AiPromptTemplate;
import com.solo.ai.mapper.AiPromptTemplateMapper;
import com.solo.ai.request.AiPromptTemplatePageRequest;
import com.solo.ai.request.AiPromptTemplateRequest;
import com.solo.ai.service.AiPromptTemplateService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词模板服务实现类
 */
@Service
public class AiPromptTemplateServiceImpl extends ServiceImpl<AiPromptTemplateMapper, AiPromptTemplate> implements AiPromptTemplateService {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    @Override
    public Page<AiPromptTemplate> pageQuery(AiPromptTemplatePageRequest req) {
        Long page = req.getPage() != null ? req.getPage() : 1L;
        Long pageSize = req.getPageSize() != null ? req.getPageSize() : 20L;
        Page<AiPromptTemplate> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<AiPromptTemplate> wrapper = new LambdaQueryWrapper<>();
        if (req.getTemplateName() != null && !req.getTemplateName().isBlank()) {
            wrapper.like(AiPromptTemplate::getTemplateName, req.getTemplateName());
        }
        if (req.getTemplateCode() != null && !req.getTemplateCode().isBlank()) {
            wrapper.like(AiPromptTemplate::getTemplateCode, req.getTemplateCode());
        }
        if (req.getTemplateType() != null && !req.getTemplateType().isBlank()) {
            wrapper.eq(AiPromptTemplate::getTemplateType, req.getTemplateType());
        }
        if (req.getStatus() != null) {
            wrapper.eq(AiPromptTemplate::getStatus, req.getStatus());
        }
        wrapper.orderByAsc(AiPromptTemplate::getSort).orderByDesc(AiPromptTemplate::getCreateTime);
        return page(pageParam, wrapper);
    }

    @Override
    public void createTemplate(AiPromptTemplateRequest request) {
        AiPromptTemplate existing = getOne(new LambdaQueryWrapper<AiPromptTemplate>()
                .eq(AiPromptTemplate::getTemplateCode, request.getTemplateCode()));
        if (existing != null) {
            throw new ServiceException("模板编码已存在");
        }
        AiPromptTemplate template = new AiPromptTemplate();
        template.setTemplateName(request.getTemplateName());
        template.setTemplateCode(request.getTemplateCode());
        template.setTemplateType(request.getTemplateType());
        template.setContent(request.getContent());
        template.setVariables(request.getVariables());
        template.setExample(request.getExample());
        template.setStatus(request.getStatus());
        template.setSort(request.getSort());
        template.setRemark(request.getRemark());
        save(template);
    }

    @Override
    public void updateTemplate(Long id, AiPromptTemplateRequest request) {
        AiPromptTemplate template = getById(id);
        if (template == null) {
            throw new ServiceException("模板不存在");
        }
        if (request.getTemplateName() != null) template.setTemplateName(request.getTemplateName());
        if (request.getTemplateCode() != null) template.setTemplateCode(request.getTemplateCode());
        if (request.getTemplateType() != null) template.setTemplateType(request.getTemplateType());
        if (request.getContent() != null) template.setContent(request.getContent());
        if (request.getVariables() != null) template.setVariables(request.getVariables());
        if (request.getExample() != null) template.setExample(request.getExample());
        if (request.getStatus() != null) template.setStatus(request.getStatus());
        if (request.getSort() != null) template.setSort(request.getSort());
        if (request.getRemark() != null) template.setRemark(request.getRemark());
        updateById(template);
    }

    @Override
    public AiPromptTemplate getByCode(String templateCode) {
        return lambdaQuery()
                .eq(AiPromptTemplate::getTemplateCode, templateCode)
                .eq(AiPromptTemplate::getStatus, 1)
                .one();
    }

    @Override
    public String renderTemplate(String templateCode, Map<String, Object> variables) {
        AiPromptTemplate template = getByCode(templateCode);
        if (template == null) {
            throw new ServiceException("模板不存在: " + templateCode);
        }

        String content = template.getContent();
        if (variables == null || variables.isEmpty()) {
            return content;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = variables.get(varName);
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @Override
    public List<AiPromptTemplate> getEnabledList() {
        return lambdaQuery()
                .eq(AiPromptTemplate::getStatus, 1)
                .orderByAsc(AiPromptTemplate::getSort)
                .list();
    }
}
