package com.solo.ai.request;

import lombok.Data;

/**
 * AI 模型配置分页查询请求参数
 *
 * <p>对应 {@code POST /api/ai/model-config/page}。所有字段都可选。</p>
 */
@Data
public class AiModelConfigPageRequest {
    /** 当前页码，从 1 开始，默认 1 */
    private Long page = 1L;
    /** 每页条数，默认 20 */
    private Long pageSize = 20L;
    /** 按厂商过滤（可选）；取值为 {@code provider-options} 接口返回的 {@code value}，如 {@code openai} / {@code deepseek} / {@code kimi} / {@code qwen} / {@code zhipu} / {@code ollama} / {@code anthropic} / {@code gemini} 等 */
    private String provider;
    /** 按模型类型过滤（可选）：{@code TEXT} 对话 / {@code IMAGE} 生图 / {@code VIDEO} 生视频 / {@code EMBEDDING} 向量 / {@code AUDIO} 语音 */
    private String modelType;
    /** 按模型名称模糊过滤（可选），如 {@code gpt-4o} / {@code claude-3-5-sonnet} */
    private String modelName;
    /** 状态过滤（可选）：{@code 0} 禁用 / {@code 1} 启用 */
    private Integer status;
}
