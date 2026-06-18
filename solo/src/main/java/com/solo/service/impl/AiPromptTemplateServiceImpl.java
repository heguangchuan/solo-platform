package com.solo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.entity.AiPromptTemplate;
import com.solo.mapper.AiPromptTemplateMapper;
import com.solo.service.AiPromptTemplateService;
import com.solo.common.ServiceException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt模板服务实现类
 */
@Service
public class AiPromptTemplateServiceImpl extends ServiceImpl<AiPromptTemplateMapper, AiPromptTemplate> implements AiPromptTemplateService {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

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
}
