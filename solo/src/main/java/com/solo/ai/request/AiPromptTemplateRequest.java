package com.solo.ai.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 提示词模板新增 / 编辑请求体
 *
 * <p>对应 {@code POST /api/ai/prompt-template} 与 {@code PUT /api/ai/prompt-template/{id}}。
 * {@code content} 内可使用占位符（默认 {@code ${var}} 形式），通过 {@code variables} 字段定义变量名 / 含义 / 示例。
 * 编辑时整对象覆盖，{@code variables} JSON 字符串一并替换。</p>
 */
@Schema(description = "提示词模板新增 / 编辑请求体")
@Data
public class AiPromptTemplateRequest {
    /** 必填；模板名称，前端列表里展示 */
    @Schema(description = "模板名称，前端列表里展示")
    private String templateName;
    /** 必填；模板编码，系统内唯一，渲染接口按此定位（如 {@code TRANSLATE_EN}） */
    @Schema(description = "模板编码（系统内唯一）")
    private String templateCode;
    /** 必填；模板类型：{@code CHAT} 对话 / {@code COPYWRITING} 文案 / {@code IMAGE} 生图 / {@code VIDEO} 生视频 / {@code TRANSLATION} 翻译 */
    @Schema(description = "模板类型：CHAT 对话 / COPYWRITING 文案 / IMAGE 生图 / VIDEO 生视频 / TRANSLATION 翻译")
    private String templateType;
    /** 必填；模板内容，含 {@code ${var}} 占位符 */
    @Schema(description = "模板内容，含 ${var} 占位符")
    private String content;
    /** 可选；变量定义 JSON 字符串，描述每个变量的名称 / 含义 / 示例，用于前端"填变量"表单自动渲染输入框 */
    @Schema(description = "变量定义 JSON 字符串")
    private String variables;
    /** 可选；示例输出，前端展示用 */
    @Schema(description = "示例输出，前端展示用")
    private String example;
    /** 可选；状态：{@code 0} 禁用 / {@code 1} 启用。默认启用 */
    @Schema(description = "状态：0 禁用 / 1 启用。默认启用")
    private Integer status;
    /** 可选；排序值，升序；默认 0 */
    @Schema(description = "排序值，升序；默认 0")
    private Integer sort;
    /** 可选；备注 */
    @Schema(description = "备注")
    private String remark;
}
