package com.solo.ai.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 提示词模板分页查询请求参数
 *
 * <p>对应 {@code POST /api/ai/prompt-template/page}。所有字段都可选；{@code page} / {@code pageSize} 给了默认值，
 * 前端漏传也不会 500。</p>
 */
@Schema(description = "提示词模板分页查询请求参数")
@Data
public class AiPromptTemplatePageRequest {
    /** 当前页码，从 1 开始，默认 1 */
    @Schema(description = "当前页码，从 1 开始，默认 1")
    private Long page = 1L;
    /** 每页条数，默认 20 */
    @Schema(description = "每页条数，默认 20")
    private Long pageSize = 20L;
    /** 按模板名称模糊过滤（可选） */
    @Schema(description = "按模板名称模糊过滤（可选）")
    private String templateName;
    /** 按模板编码精确过滤（可选），{@code templateCode} 系统内唯一 */
    @Schema(description = "按模板编码精确过滤（可选）")
    private String templateCode;
    /** 按模板类型过滤（可选）：{@code CHAT} 对话 / {@code COPYWRITING} 文案 / {@code IMAGE} 生图 / {@code VIDEO} 生视频 / {@code TRANSLATION} 翻译 */
    @Schema(description = "模板类型（可选）：CHAT 对话 / COPYWRITING 文案 / IMAGE 生图 / VIDEO 生视频 / TRANSLATION 翻译")
    private String templateType;
    /** 状态过滤（可选）：{@code 0} 禁用 / {@code 1} 启用 */
    @Schema(description = "状态过滤（可选）：0 禁用 / 1 启用")
    private Integer status;
}
