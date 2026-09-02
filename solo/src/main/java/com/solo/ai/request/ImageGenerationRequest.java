package com.solo.ai.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 图片生成请求体
 *
 * <p>对应 {@code POST /api/ai/task/image/generate}。支持场景：</p>
 * <ul>
 *   <li>文生图：只填 {@code prompt}</li>
 *   <li>图生图 / 局部编辑：再填 {@code image[]}（具体语义看厂商约定）</li>
 * </ul>
 *
 * <p>{@code modelConfigId} 缺省时走系统默认图片模型。具体厂商支持哪些参数见 {@code GET /api/ai/model-config/list}。</p>
 */
@Schema(description = "图片生成请求体")
@Data
public class ImageGenerationRequest {
    /** 可选；关联会话 id（用于把这次生成挂到某个创作会话的对话流里） */
    @Schema(description = "关联会话 ID")
    private Long conversationId;
    /** 可选；模型配置 id，缺省走系统默认图片模型 */
    @Schema(description = "模型配置 ID")
    private Long modelConfigId;
    /** 必填；用户提示词 */
    @Schema(description = "用户提示词")
    private String prompt;
    /** 可选；输出尺寸，如 {@code 1024x1024} / {@code 1024x1792} / {@code 1792x1024}，具体支持的值因厂商而异 */
    @Schema(description = "输出尺寸，如 1024x1024 / 1024x1792 / 1792x1024")
    private String size;
    /** 可选；生成图片张数，默认 1。响应 {@code images[]} 长度与这里一致 */
    @Schema(description = "生成图片张数，默认 1")
    private Integer n;
    /** 可选；返回格式：{@code url} 返回图片 URL / {@code b64_json} 返回 base64 编码。缺省由服务层决定，建议显式给 {@code url} */
    @Schema(description = "返回格式：url 返回图片 URL / b64_json 返回 base64 编码")
    private String responseFormat;
    /** 可选；图生图输入的图片 URL 列表，每张图需公网可访问 */
    @Schema(description = "图生图输入的图片 URL 列表")
    private List<String> image;
}
