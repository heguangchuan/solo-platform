package com.solo.ai.request;

import lombok.Data;

/**
 * 提示词模板新增/编辑请求体
 */
@Data
public class AiPromptTemplateRequest {
    private String templateName;
    private String templateCode;
    private String templateType;
    private String content;
    private String variables;
    private String example;
    private Integer status;
    private Integer sort;
    private String remark;
}
