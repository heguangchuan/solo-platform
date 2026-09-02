package com.solo.ai.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.solo.ai.entity.AiModelConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * AI 模型配置（安全视图，不返回真实 API 密钥）
 *
 * <p>对应 {@code POST /api/ai/model-config/page}、{@code GET /api/ai/model-config/list}、
 * {@code GET /api/ai/model-config/{id}} 等接口。</p>
 *
 * <h3>API Key 安全约定</h3>
 * <ul>
 *   <li>{@link #hasApiKey} 仅为布尔位：{@code true} 表示已配置过密钥（明文密钥<b>不出库</b>），
 *       {@code false} 表示从未配置</li>
 *   <li>前端编辑时若 {@code hasApiKey=true}，应展示为"已配置（留空表示不修改）"，不必重新填；
 *       {@code false} 时才需要让用户填新密钥</li>
 * </ul>
 */
@Schema(description = "AI 模型配置（安全视图）")
@Data
public class AiModelConfigResponse {

    /** 配置 ID（前端路由用） */
    @Schema(description = "配置 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /** 厂商编码（对应 {@code provider-options} 接口的 {@code value}，如 {@code openai} / {@code kimi}） */
    @Schema(description = "厂商编码")
    private String provider;
    /** 模型名称（如 {@code gpt-4o} / {@code claude-3-5-sonnet}） */
    @Schema(description = "模型名称")
    private String modelName;
    /** 模型类型：{@code TEXT} 对话 / {@code IMAGE} 生图 / {@code VIDEO} 生视频 / {@code EMBEDDING} 向量 / {@code AUDIO} 语音 */
    @Schema(description = "模型类型：TEXT 对话 / IMAGE 生图 / VIDEO 生视频 / EMBEDDING 向量 / AUDIO 语音")
    private String modelType;
    /** API 基础地址（用户填的代理地址；为空时后端走厂商默认） */
    @Schema(description = "API 基础地址")
    private String baseUrl;
    /** 模型参数 JSON 字符串，如 {@code {"temperature":0.7}} */
    @Schema(description = "模型参数 JSON 字符串")
    private String modelParams;
    /** 是否支持图片输入：{@code 0} 否 / {@code 1} 是 */
    @Schema(description = "是否支持图片输入：0 否 / 1 是")
    private Integer supportVision;
    /** 状态：{@code 0} 禁用 / {@code 1} 启用。前端按值控制开关 */
    @Schema(description = "状态：0 禁用 / 1 启用")
    private Integer status;
    /** 排序值（升序，下拉时小的在前） */
    @Schema(description = "排序值")
    private Integer sort;
    /** 是否已配置过 API Key（布尔位，<b>不是真实密钥</b>） */
    @Schema(description = "是否已配置过 API Key（布尔位）")
    private Boolean hasApiKey;

    /** 创建时间（{@code yyyy-MM-dd HH:mm:ss}，GMT+8） */
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /** 更新时间（{@code yyyy-MM-dd HH:mm:ss}，GMT+8） */
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /**
     * 把 {@link AiModelConfig} 实体转为安全视图。
     * <p>{@code apiKey} 不复制；{@link #hasApiKey} 由原实体是否有非空 {@code apiKey} 决定。</p>
     */
    public static AiModelConfigResponse from(AiModelConfig config) {
        if (config == null) return null;
        AiModelConfigResponse response = new AiModelConfigResponse();
        response.setId(config.getId());
        response.setProvider(config.getProvider());
        response.setModelName(config.getModelName());
        response.setModelType(config.getModelType());
        response.setBaseUrl(config.getBaseUrl());
        response.setModelParams(config.getModelParams());
        response.setSupportVision(config.getSupportVision());
        response.setStatus(config.getStatus());
        response.setSort(config.getSort());
        response.setCreateTime(config.getCreateTime());
        response.setUpdateTime(config.getUpdateTime());
        response.setHasApiKey(config.getApiKey() != null && !config.getApiKey().isBlank());
        return response;
    }
}
