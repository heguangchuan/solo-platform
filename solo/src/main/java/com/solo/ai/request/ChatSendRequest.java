package com.solo.ai.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 发送消息请求体
 *
 * <p>对应 {@code POST /api/ai/chat/send}（同步）与 {@code POST /api/ai/chat/stream}（SSE 流式）、
 * {@code POST /api/ai/creation/stream}（统一创作流式）三个端点。普通对话与模板对话用同一对象：</p>
 * <ul>
 *   <li>{@code templateCode} 非空 → 走模板对话分支，{@code variables} 生效</li>
 *   <li>{@code templateCode} 为空 → 普通对话，{@code content} 必填</li>
 * </ul>
 *
 * <p>多模态：{@code images} 给多模态模型（视觉）使用，<b>每张图必须为可公开访问的 URL</b>；
 * 后端会拉下来转发给模型，并落库到 {@code ai_asset}。</p>
 */
@Schema(description = "发送消息请求体")
@Data
public class ChatSendRequest {

    /** 必填（新建会话后可为空）；会话 id，留空时由后端自动创建新会话 */
    @Schema(description = "会话 ID（新建会话可为空）")
    private Long conversationId;

    /** 必填（模板分支可选）；用户消息内容，模板分支里也会作为附加上下文传给 LLM */
    @Schema(description = "用户消息内容")
    private String content;

    /** 可选；使用的模型配置 id，为空时使用该用户默认启用的文本模型。具体可用 model 见 {@code GET /api/ai/model-config/list} */
    @Schema(description = "模型配置 ID")
    private Long modelConfigId;

    /** 可选；模板编码（使用模板对话时必填，例 {@code TRANSLATE_EN}） */
    @Schema(description = "模板编码")
    private String templateCode;

    /** 可选；模板变量（{@code key → value}），仅在 {@link #templateCode} 非空时生效。例：{@code {"text":"你好"}} */
    @Schema(description = "模板变量（key→value 替换）")
    private Map<String, Object> variables;

    /** 可选；多模态图片输入：每张图给出可访问 URL，发给模型并落库 ai_asset */
    @Schema(description = "多模态图片输入列表")
    private List<AttachmentInput> images;

    /**
     * 多模态附件输入项
     */
    @Schema(description = "多模态附件输入项")
    @Data
    public static class AttachmentInput {
        /** 必填；图片访问 URL，需公网可访问（后端会主动拉取转发给模型） */
        @Schema(description = "图片访问 URL（需公网可访问）")
        private String url;
        /** 可选；文件原名，仅用于展示 */
        @Schema(description = "文件原名，仅用于展示")
        private String fileName;
        /** 可选；MIME 类型，例如 {@code image/png} / {@code image/jpeg} / {@code image/webp} */
        @Schema(description = "MIME 类型，例如 image/png / image/jpeg / image/webp")
        private String mimeType;
    }
}
