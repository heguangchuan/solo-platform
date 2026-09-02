package com.solo.ai.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 模型配置新增 / 编辑请求体
 *
 * <p>对应 {@code POST /api/ai/model-config} 与 {@code PUT /api/ai/model-config/{id}}。
 * 编辑时 {@code apiKey} 留空 → 保留原值；填了新值才覆盖。响应不返回真实密钥，只有
 * {@code hasApiKey} 布尔位。</p>
 */
@Schema(description = "AI 模型配置新增 / 编辑请求体")
@Data
public class AiModelConfigRequest {
    /** 必填；厂商编码，取值见 {@code GET /api/ai/model-config/provider-options} 的 {@code value}，如 {@code openai} / {@code deepseek} / {@code kimi} / {@code qwen} / {@code zhipu} / {@code ollama} / {@code anthropic} / {@code gemini} / {@code custom} 等 */
    @Schema(description = "厂商编码（openai/deepseek/kimi 等）")
    private String provider;
    /** 必填；模型名称（具体可用 model 列表见厂商文档） */
    @Schema(description = "模型名称")
    private String modelName;
    /** 必填；模型类型：{@code TEXT} 对话 / {@code IMAGE} 生图 / {@code VIDEO} 生视频 / {@code EMBEDDING} 向量 / {@code AUDIO} 语音 */
    @Schema(description = "模型类型：TEXT 对话 / IMAGE 生图 / VIDEO 生视频 / EMBEDDING 向量 / AUDIO 语音")
    private String modelType;
    /** 必填（新增时）；编辑时留空表示不修改。API 密钥明文传输，后端加密存储，响应只返回 {@code hasApiKey} 布尔位 */
    @Schema(description = "明文 API Key，仅 HTTPS 传输；留空 = 不修改")
    private String apiKey;
    /** 可选；API 基础地址，留空时使用厂商默认地址。代理 / 自建兼容接入点时填，例如 {@code https://your-proxy.com/v1} */
    @Schema(description = "API 基础地址，留空时使用厂商默认地址")
    private String baseUrl;
    /** 可选；模型参数 JSON 字符串，如 {@code {"temperature":0.7,"max_tokens":4096}}；具体字段因厂商而异 */
    @Schema(description = "模型参数 JSON 字符串，如 {\"temperature\":0.7,\"max_tokens\":4096}")
    private String modelParams;
    /** 可选；是否支持图片输入：{@code 0} 否 / {@code 1} 是。对话模型多模态能力由这里控制 */
    @Schema(description = "是否支持图片输入：0 否 / 1 是")
    private Integer supportVision;
    /** 可选；状态：{@code 0} 禁用 / {@code 1} 启用。默认启用 */
    @Schema(description = "状态：0 禁用 / 1 启用。默认启用")
    private Integer status;
    /** 可选；排序值，升序；默认 0 */
    @Schema(description = "排序值，升序；默认 0")
    private Integer sort;
}
