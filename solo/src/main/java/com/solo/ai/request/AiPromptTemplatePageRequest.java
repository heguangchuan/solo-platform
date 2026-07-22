package com.solo.ai.request;

import lombok.Data;

/**
 * 提示词模板分页查询请求参数
 */
@Data
public class AiPromptTemplatePageRequest {
    private Long page = 1L;
    private Long pageSize = 20L;
    private String templateName;
    private String templateCode;
    private String templateType;
    private Integer status;
}
